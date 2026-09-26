# ADR 0002: Hybrid terminal: built-in PTY plus optional Termux

Status: Accepted

Date: 2026-09-26

## Context

Android does not ship a desktop-like shell environment, but the agent and its users need to run commands: the Bash tool, hooks, and a manual terminal screen. The platform imposes hard constraints:

- Since Android 10 (API 29), apps cannot execute binaries from app data (W^X, `app_data_file execute_no_trans`).
- The stock shell is mksh with toybox applets; bash, git, node, and python are absent.
- Interactive programs need a real PTY, which `ProcessBuilder` cannot provide.

Termux solves the userland problem but is a third-party app with its own permission model (`com.termux.permission.RUN_COMMAND`, `allow-external-apps=true` in termux.properties) and output size limits (about 100 KB per invocation). Termux cannot be required: the terminal and the Bash tool must work on a stock device with nothing else installed.

## Decision

The terminal subsystem is hybrid:

1. **Built-in PTY terminal.** A small JNI C module forks the shell on a real PTY (`/dev/ptmx`, `grantpt`/`unlockpt`/`ptsname`, fork, `setsid`, slave `dup2`, `execv` of `/system/bin/sh`), following the AOSP Terminal app pattern. It powers the Terminal screen and a one-shot exec service for the Bash tool and hooks.
2. **Terminal rendering via connectbot/termlib** (Apache-2.0, embedding MIT libvterm). Upstream publishes no Maven artifact, and its JitPack build fails because the upstream build applies Maven signing unconditionally. Milestone M1 therefore vendored upstream version 0.3.6 (commit `1cc3677`) into `:core:terminal`; provenance and local modifications are recorded in `core/terminal/VENDORED.md`.
3. **Termux as an optional enhancer.** `TermuxBridge` unifies `exec` behind one API with an internal PTY implementation and a Termux RUN_COMMAND implementation (background for tool execution, foreground to hand a session to the Termux app). Settings pick the mode; the automatic default is Termux when ready, otherwise the internal shell.
4. **No bundled userland in v1.** Shipping `bash`, `git`, or `ripgrep` as `jniLibs` binaries (executed from `nativeLibraryDir`, 16 KB page aligned, with GPL source offers where required) stays on the roadmap after 1.0.

## Consequences

- The terminal works on any Android 8.0 device with zero dependencies, at the cost of a reduced stock userland that is communicated in the UI.
- Interactive programs need the JNI PTY to behave correctly; pipe based fallbacks are only acceptable for trivial non-interactive command execution.
- Termux integration has per-user setup friction (permission grant plus termux.properties change); the app must show a guided status screen instead of failing silently.
- Output truncation is a first-class concern in Termux mode and is handled with cache files readable by later tool calls.

## References

- AOSP Terminal forkpty reference: https://android.googlesource.com/platform/packages/apps/Terminal/+/master/jni/forkpty.cpp
- Android 10 behavior changes (W^X): https://developer.android.com/about/versions/10/behavior-changes-10
- Termux RUN_COMMAND intent: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- termlib: https://github.com/connectbot/termlib
- 16 KB page sizes: https://developer.android.com/guide/practices/page-sizes
