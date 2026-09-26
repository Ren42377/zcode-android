# Vendored code: connectbot/termlib

This directory (Kotlin sources under `src/main/java/org/connectbot/terminal`, resources under `src/main/res`, and the native code under `src/main/cpp`) contains a vendored copy of [connectbot/termlib](https://github.com/connectbot/termlib).

- Upstream version: tag `0.3.6`, commit `1cc3677199ca8400c7e876ff8213c1626c1ffc2c`.
- Upstream license: Apache-2.0 (see `LICENSE` at the repository root). The bundled libvterm is MIT (see `src/main/cpp/libvterm/LICENSE`).
- Why vendored: the upstream project does not publish to Maven Central, and its JitPack build fails because the upstream build applies Maven signing unconditionally, which has no signing key on JitPack. Vendoring is the fallback sanctioned by [ADR 0002](../../docs/adr/0002-terminal-hybrid.md).

## Local modifications

Local changes are kept to the minimum needed to build inside this project:

- `build.gradle.kts` of this module (not part of the vendored tree) applies the project convention plugin instead of the upstream build file.
- `src/main/cpp/CMakeLists.txt` gains the `zcodepty` target for the PTY implementation owned by this project (added in a later commit, marked with a comment).

When upgrading, re-apply the marked local modifications on top of the new upstream sources.
