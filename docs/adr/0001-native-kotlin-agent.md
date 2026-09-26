# ADR 0001: Native Kotlin agent engine

Status: Accepted

Date: 2026-09-26

## Context

ZCode ships its agent as a Node.js bundle (`zcode.cjs`) that requires Node.js 24 and renders through a terminal UI built on OpenTUI and React. Android has no official Node.js runtime, running one inside the app sandbox would be heavy on memory and battery, and the TUI is keyboard driven, which does not transfer to touch screens. There is no official ZCode client for Android.

The product requirement is feature parity with ZCode (streaming conversation, tool calls, permission modes, plan approval, subagents, todos, slash commands, skills, plugins, hooks, MCP, memory, sessions) while the UI follows the official terminal theme with touch interactions.

## Decision

The agent engine is reimplemented natively in Kotlin across the `:core:agent`, `:core:engine`, and `:core:tools` modules. The app never executes `zcode.cjs`.

Compatibility with ZCode is kept at the protocol and file format level, not at the code level:

- Tool names and their semantics (Read, Write, Edit, Glob, Grep, Bash, TodoWrite, WebFetch, WebSearch, Task, AskUserQuestion) match the original implementations.
- Permission mode names and behavior (plan, build, edit, yolo) match.
- Configuration, skills, commands, plugins, hooks, and memory follow the original file formats so content moves between desktop ZCode and this app by copying folders.
- Rendering of messages, tool cards, diffs, approvals, and todos follows the official TUI theme and component design.

Before implementing any feature, the corresponding source in `zai-org/ZCode` (`packages/tui`, `packages/core`, `packages/contracts`) and the official docs at `zcode.z.ai/en/docs` are read; this document does not replace that research.

## Consequences

- Full control over performance and the touch UX, at the cost of re-implementing the agent loop and tool set.
- Behavior drift becomes possible, so every v1 feature carries an explicit requirement to match the original implementation and verify it against the ZCode sources.
- The original TypeScript code serves as a behavioral reference only; no code is copied (ZCode is Apache-2.0, so copying would be permitted, but a native rewrite is cleaner than a mechanical port).

## References

- ZCode repository: https://github.com/zai-org/ZCode
- ZCode documentation: https://zcode.z.ai/en/docs
