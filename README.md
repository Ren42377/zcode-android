# ZCode for Android

A native Android client for ZCode, the agentic development environment by Z.ai. The app brings the ZCode agent workflow to phones and tablets: chat with an AI coding agent, watch its tool calls and diffs stream in, approve its edits, and run shell commands from a built-in terminal.

The working product name is "ZCode for Android"; the final name and branding are a separate decision.

> This project is under active development. The current implementation status is tracked in [docs/roadmap.md](docs/roadmap.md).

## Features planned for version 1.0

- Streaming chat with tool calls, permission modes (plan, build, edit, yolo), and approval panels, styled after the official ZCode terminal theme.
- ZCode compatible file formats: skills, custom slash commands, plugins, hooks, and MCP configuration.
- Built-in terminal based on a real PTY and the stock Android shell, with optional Termux integration for a full userland (bash, git, node).
- API key authentication against the Z.ai Open Platform, the Z.ai Coding Plan, and BigModel endpoints.

See the feature matrix in [docs/roadmap.md](docs/roadmap.md) for the complete list with statuses.

## Screenshots

Screenshots will be published here as user-facing screens land. The foundation milestone currently ships the design token preview screen.

## Install

1. Download an APK from GitHub Releases:
   - Development builds are attached to the `latest-build` prerelease, which is refreshed on every commit to `main`.
   - Stable builds are attached to version tags such as `v0.1.0`.
2. Open the APK on the device and allow package installation from the app you downloaded it with.
3. Follow onboarding: paste your API key, pick a provider preset, and create a workspace.

Signed release builds ship as an `arm64-v8a` APK and a universal APK. Development builds are universal debug APKs.

## API key

Onboarding asks for a Z.ai API key. Provider presets:

| Preset | Base URL |
| --- | --- |
| Z.ai Open Platform (pay as you go) | `https://api.z.ai/api/paas/v4` |
| Z.ai Coding Plan | `https://api.z.ai/api/coding/paas/v4` |
| Z.ai Coding Plan (Anthropic compatible) | `https://api.z.ai/api/anthropic` |
| BigModel | `https://open.bigmodel.cn/api/paas/v4` |
| BigModel Coding Plan | `https://open.bigmodel.cn/api/coding/paas/v4` |
| Custom (OpenAI compatible) | user defined |

The key is validated with one lightweight request, then stored in Android Keystore backed encrypted storage. It is never written to plain text configuration files or logs.

## Documentation

- [docs/architecture.md](docs/architecture.md): module structure, technology choices, and storage layout.
- [docs/terminal.md](docs/terminal.md): how the terminal subsystem works, including platform constraints and Termux integration.
- [docs/roadmap.md](docs/roadmap.md): feature matrix and milestone status.
- [docs/adr](docs/adr): architecture decision records.

## Building

All builds, unit tests, and lints run on GitHub Actions; this repository does not support local builds by policy. The CI setup is described in [docs/architecture.md](docs/architecture.md).

## Limitations

- The stock Android shell (mksh with toybox applets) has no bash, git, node, or python. Install Termux and enable Termux mode for a full userland.
- Only API key authentication is supported. There is no OAuth or account login.
- Distribution is via GitHub Releases only; the app is not published on Google Play.

## License

Apache-2.0. See [LICENSE](LICENSE).
