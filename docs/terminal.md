# Terminal subsystem

This document describes how the built-in terminal works: the Android platform constraints it has to respect, the layered design, the PTY implementation, the terminal emulator, the Termux integration, and how the agent's Bash tool uses the same machinery. Status: designed for milestone M1. This page records the decisions and constraints so implementation does not have to rediscover them.

## Platform constraints

1. **W^X on Android 10 (API 29 and above).** Apps with targetSdk 29 or higher may not execute binaries stored in app data (`app_data_file execute_no_trans`). Two consequences:
   - Extracting an executable into `filesDir` and calling `exec` on it is forbidden and would fail on modern devices.
   - Bundled binaries, if ever shipped, must be packed as `jniLibs/<abi>/lib*.so` with `android:extractNativeLibs="true"` and executed from `applicationInfo.nativeLibraryDir`, which is still allowed. Bundling its own userland binaries is explicitly out of scope for version 1.0 and lives on the roadmap.
2. **Stock Android shell.** `/system/bin/sh` is mksh. Toybox provides applets such as `ls`, `cp`, `mv`, `grep`, and `sed`. There is no bash, git, node, or python on a stock device. The built-in terminal therefore works everywhere but with a reduced userland; Termux integration closes that gap when installed.
3. **PTY is required.** `java.lang.ProcessBuilder` only provides pipes, which is not enough for interactive programs. A real PTY is allocated through JNI: open `/dev/ptmx`, run `grantpt`/`unlockpt`/`ptsname` (all supported by bionic), fork, and in the child call `setsid`, open the slave `/dev/pts/N`, `dup2` it onto file descriptors 0, 1, and 2, then `execv` the shell. The AOSP Terminal app (`packages/apps/Terminal`, `jni/forkpty.cpp`) is the reference pattern. One quirk matters: reading from the master returns `EIO` when the child exits; the code treats that as a normal end of session.
4. **Package visibility (API 30 and above).** Interacting with Termux requires `<queries><package android:name="com.termux"/></queries>` in the manifest.
5. **Termux permission.** The app declares the custom permission `com.termux.permission.RUN_COMMAND` and requests it at runtime on the Termux mode screen. On the Termux side the user must set `allow-external-apps=true` in `~/.termux/termux.properties` and run `termux-reload-settings`.
6. **Termux output limits.** Combined stdout plus stderr per invocation is limited to roughly 100 KB, and intent extras to roughly 500 KB. The Bash tool in Termux mode truncates output and stores the remainder in a cache file that a later tool call can read.
7. **16 KB page size.** When bundled native libraries are introduced on the roadmap, they must be 16 KB aligned (NDK r27 or newer with `-Wl,-z,max-page-size=16384`) to satisfy targetSdk 35+ requirements.

## Layered design

All terminal code lives in `:core:terminal`:

| Layer | Responsibility |
| --- | --- |
| `PtyChannel` | JNI wrapper: open and close a PTY, read and write threads, `EIO` handling, window resize via `ioctl TIOCSWINSZ` |
| `ShellSession` | One shell process (pid, working directory, environment). Minimal env: `PATH` contains `/system/bin` and the `nativeLibraryDir`, `HOME` points into app storage, `TMPDIR` into the cache directory |
| `TerminalEmulator` and `TerminalView` | VT rendering from connectbot/termlib (Apache-2.0, which embeds libvterm under MIT) as a Compose component |
| `ExecService` | One-shot command execution with collected output, built on the PTY so behavior matches the interactive terminal. Used by the Bash tool and by hooks |
| `TermuxBridge` | Optional bridge to Termux over the RUN_COMMAND intent |

## PTY via JNI

1. A small C module (`src/main/cpp/pty.c`) exposes `pty_fork_exec(argv[], cwd, env[], rows, cols)` returning the master fd and pid, plus `pty_read`, `pty_write`, `pty_resize`, `pty_close`, and `pty_kill`.
2. Kotlin registers the library via `System.loadLibrary("zcodepty")` and exposes a suspend API on `Dispatchers.IO`.
3. The default shell is `/system/bin/sh` (mksh).
4. All NDK compilation happens on GitHub Actions (ubuntu-latest with the NDK bundled or installed there), never on a local machine.

## Terminal emulator

The renderer is `com.github.connectbot:termlib`. Requirements: 256 color and truecolor support, scrolling, selection and copy, adjustable font size, and double-width CJK characters.

Known issue recorded during the foundation milestone: the JitPack build for `com.github.connectbot:termlib:0.3.6` did not resolve on 2026-09-26 (the POM request returned 404). The version catalog pins the coordinate, but nothing depends on it yet, so no build resolves it. When milestone M1 wires the dependency, it must either confirm a working JitPack tag or fall back to vendoring the Apache-2.0 sources into this repository, as allowed by [ADR 0002](adr/0002-terminal-hybrid.md).

Tool output from the Bash tool is not rendered in the TerminalView. It appears as a tool card in the chat transcript, the same way ZCode shows tool output in its transcript.

## Termux integration

1. **Detection.** `PackageManager` checks for `com.termux` (with the `<queries>` entry). When it is missing, the UI shows "Termux not installed" with links to F-Droid and the Termux GitHub releases, and every feature keeps working on the internal shell.
2. **Invocation.** Explicit service intent `com.termux.RUN_COMMAND` to `com.termux.app.RunCommandService` via `setClassName("com.termux", "com.termux.app.RunCommandService")` with extras `EXTRA_COMMAND_PATH` (supporting `$PREFIX/` and `~/` prefixes), `EXTRA_ARGUMENTS`, `EXTRA_WORKDIR`, `EXTRA_BACKGROUND`, `EXTRA_SESSION_ACTION`, `EXTRA_STDIN`, and `EXTRA_PENDING_INTENT` with a unique request code per call (requires Termux 0.109 or newer).
3. **Results.** Background invocations return stdout, stderr, and the exit code within the size limits above. Foreground invocations open a session in the Termux app; the UI uses them for an "Open full session in Termux" action.
4. **Unification.** `TermuxBridge` exposes one API, `exec(command): ExecResult`, with two implementations: `InternalPtyExec` and `TermuxExec`. Settings pick the default mode; the automatic default is Termux when it is ready, otherwise the internal PTY.
5. **Bootstrap.** On first Termux use the app offers to run `pkg update && pkg install -y git` after explicit user confirmation.

## The agent's Bash tool

1. Default execution path is the internal `ExecService` on mksh, with `PATH=/system/bin:<nativeLibraryDir>`; when Termux mode is active, `$PREFIX/bin` and the Termux `$HOME` are added.
2. In Termux mode the Bash tool uses `TermuxBridge` background invocations to reach bash, git, and node, truncating output to the supported limit and storing the remainder in a cache file readable by later tool calls.
3. Commands default to a 120 second timeout (configurable). Long-running commands (dev servers, builds) run in background mode with output streamed to a file and a notification on completion; the Bash tool can query their status.
4. The working directory is the workspace root. File tools validate paths against the workspace; shell commands that leave the workspace trigger a warning outside of yolo mode.
5. Each session keeps a shell snapshot (environment and working directory) so consecutive commands continue where the previous one stopped, mirroring the ZCode shell-snapshots behavior.

Interactive programs launched by the tool are directed to the Terminal screen instead of blocking the chat.
