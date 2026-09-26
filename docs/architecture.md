# Architecture

This document describes the architecture of ZCode for Android: the module structure, the technology stack, the dependency rules between modules, the on-device storage layout, and the CI setup. It reflects the foundation milestone; sections marked as planned describe the target design that later milestones implement.

## Overview

The app is a native Kotlin Android application that reimplements the ZCode agent experience for touch devices. Three principles drive the design:

1. Core agent behavior matches ZCode: streaming conversation, tool calls, permission modes, plan approval, subagents, todos, slash commands, skills, plugins, hooks, MCP, memory, and session management. File formats (skills, commands, plugins, configuration) follow the original ZCode formats so they stay interchangeable.
2. The visual language mirrors the ZCode terminal theme. The color tokens in `:core:designsystem` are pinned by unit tests to the values from the official TUI theme (`apps/zcode-cli/packages/tui/src/theme/defaults.ts` in `zai-org/ZCode`).
3. Authentication uses an API key only. There is no OAuth and no account login.

The agent engine is written natively in Kotlin rather than running the original `zcode.cjs` bundle. See [ADR 0001](adr/0001-native-kotlin-agent.md) for the reasoning.

## Module structure

| Gradle module | Purpose |
| --- | --- |
| `:app` | Application entry point, navigation, dependency graph |
| `:core:designsystem` | Color tokens, typography, shared UI components |
| `:core:agent` | Agent loop, tool orchestration, compaction, permission modes (planned, milestone M2 and later) |
| `:core:engine` | LLM clients: OpenAI compatible and Anthropic compatible, SSE streaming (planned, M2) |
| `:core:tools` | Tool implementations: Read, Write, Edit, Glob, Grep, Bash, TodoWrite, WebFetch, WebSearch, Task, AskUserQuestion (planned, M3) |
| `:core:terminal` | PTY via JNI, shell sessions, exec service, Termux bridge, terminal emulator wrapper (planned, M1) |
| `:core:mcp` | MCP client for stdio, HTTP, and SSE transports (planned, M4) |
| `:core:storage` | Room database for sessions, messages, and usage; DataStore preferences; encrypted key storage (planned, M2) |
| `:core:config` | ZCode compatible config files, skills, commands, and plugin loaders (planned, M4) |
| `:feature:chat` | Chat screen and transcript components (planned, M2 and later) |
| `:feature:terminal` | Terminal screen (planned, M1) |
| `:feature:workspace` | File explorer and workspace picker (planned, M5) |
| `:feature:settings` | Settings screens (planned, M5) |

All modules exist since the foundation milestone so that the dependency rules apply from the first build. Modules without sources contain only their Gradle build file; sources land together with the milestone that owns them.

## Dependency rules

The layering rules are fixed and enforced by review of the module build files:

- `:app` depends on every feature module and on `:core:designsystem`. Feature modules never depend on each other.
- Feature modules depend on core modules that own the APIs they consume. A feature module must not reach into the internals of another feature.
- Core modules depend on other core modules only in the direction of higher to lower level: `:core:agent` may use `:core:engine`, `:core:tools`, `:core:mcp`, `:core:config`, and `:core:storage`; `:core:tools` may use `:core:terminal`; `:core:mcp` may use `:core:engine`. `:core:designsystem`, `:core:engine`, `:core:terminal`, `:core:storage`, and `:core:config` do not depend on other core modules.
- One piece of logic lives in exactly one module. Before adding a new helper, the existing modules are searched for an equivalent.

These dependencies are wired incrementally as the code that uses them lands, to avoid unused declarations.

## Technology stack

| Aspect | Choice |
| --- | --- |
| Language | Kotlin 2.4.20, JDK 17 |
| Build | AGP 9.4.1 with built-in Kotlin, Gradle 9.8.0 |
| UI | Jetpack Compose, Compose BOM 2026.09.00, Material 3 with a custom color scheme mapped from the ZCode tokens |
| Dependency injection | Hilt 2.60.1 |
| Async | Coroutines 1.11.0 and Flow |
| Networking | OkHttp 5.5.0 with kotlinx.serialization 1.11.0 |
| Database | Room 2.8.5 |
| Preferences | DataStore 1.2.1 plus JSON config files with kotlinx.serialization |
| Terminal UI | connectbot/termlib plus a JNI PTY implementation (see [docs/terminal.md](terminal.md)) |
| Images | Coil 3.6.3 |
| SDK levels | minSdk 26, targetSdk 35, compileSdk 37 |

All versions are pinned in `gradle/libs.versions.toml`. The rule is: latest stable, non deprecated versions only. Alpha, beta, and release candidate versions are not used.

### Build logic

Shared Android configuration lives in the `build-logic` included build as two convention plugins, `zcode.android.application` and `zcode.android.library`. They set the SDK levels (compileSdk 37, targetSdk 35, minSdk 26), the Java 17 compatibility, and the AGP plugin application in one place. Module build files therefore only declare their namespace and their specific dependencies.

AGP 9 enables built-in Kotlin: the `org.jetbrains.kotlin.android` plugin is not applied anywhere. Compose modules apply `org.jetbrains.kotlin.plugin.compose`. Because AGP 9 bundles Kotlin Gradle Plugin 2.2.10 by default, the root build file raises the KGP version to the catalog version on the buildscript classpath, as documented in the AGP 9.0 release notes.

## On-device storage layout

App-private storage mirrors the `~/.zcode` structure of the desktop CLI:

| App location | ZCode equivalent | Content |
| --- | --- | --- |
| `files/config/v2/setting.json` | `~/.zcode/v2/setting.json` | App preferences: theme, memory on/off, terminal mode |
| `files/config/v2/provider_config.json` | `~/.zcode/v2/provider_config.json` | Providers and models; the API key is a reference into encrypted storage, not plain text |
| `files/config/cli/config.json` | `~/.zcode/cli/config.json` | MCP servers, hooks, plugins |
| `files/config/skills/`, `commands/`, `agents/` | equivalents | Skills, custom commands, custom subagents |
| `files/memory/<project-hash>/` | `~/.zcode/cli/memories/...` | MEMORY.md index and fact files |
| Room database | `cli/db/db.sqlite` | Sessions, messages, tool events, usage |
| `<workspace>/.zcode/config.json` | same | Project scoped MCP, hooks, and commands |

One deliberate deviation from ZCode: credentials are always stored encrypted with an Android Keystore backed key. ZCode stores API keys in plain text configuration and OAuth tokens in an encrypted credentials file. See [ADR 0003](adr/0003-api-key-auth.md).

## Security

- The API key lives only in Android Keystore backed encrypted storage. It never appears in logs, exports, or plain text files.
- There is no telemetry. On-device logs for debugging never include prompt content by default.
- All traffic uses HTTPS with the system certificate store.
- File tools are sandboxed to the selected workspace; access outside the workspace requires an explicit approval.
- Termux integration only runs commands that passed the permission mode checks, and command output is truncated before it enters the conversation.

## CI and release

GitHub Actions owns every build, test, and lint run. There are three workflows:

- `ci.yml`: runs on every push to `main` and every pull request. Jobs: ktlint, Android Lint, unit tests, and `assembleDebug`. The debug APK is uploaded as an artifact, and pushes to `main` refresh the `latest-build` prerelease.
- `release.yml`: runs on `v*` tags. It builds signed release APKs from GitHub Actions secrets, produces an `arm64-v8a` APK and a universal APK named `zcode-android-vX.Y.Z-...apk`, and publishes the GitHub release with a changelog generated from commits.
- `wrapper.yml`: one-shot maintenance workflow that generates the Gradle wrapper and commits it, so builds are reproducible.

Signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. While they are unset, debug builds stay green with debug signing and a tag release fails fast with a clear message. Local Gradle execution is not part of the workflow by policy.
