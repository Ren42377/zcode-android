# Roadmap

This page tracks the feature parity matrix with ZCode and the release milestones. Status values: **v1** means required for the 1.0 release, **Roadmap** means planned after 1.0, **Out of scope** means not built. Before implementing any v1 feature, the original implementation in `zai-org/ZCode` is read and matched, so names, semantics, and file formats stay compatible.

## Feature matrix

| Feature | Status | Notes |
| --- | --- | --- |
| Streaming conversation (text, thinking, tool calls) | v1 | Thinking rendered dimmed above the answer |
| Tools: Read, Write, Edit, Glob, Grep, Bash, TodoWrite, WebFetch, WebSearch, Task, AskUserQuestion | v1 | Names and semantics follow ZCode |
| Dynamic MCP tools (`mcp__server__tool`) | v1 | |
| Permission modes: plan, build, edit, yolo with mode cycling | v1 | |
| Approval panel (Allow, Always Allow, Reject, Always Reject) | v1 | Always decisions stored per project |
| Plan mode with plan approval | v1 | |
| Todo list (TodoWrite) and todo widget | v1 | |
| Built-in slash commands (/help, /compact, /goal, /mcp, /skill, /model, /mode, /effort, /clear, /resume, /init, /memory) | v1 | |
| Custom markdown commands (frontmatter, $ARGUMENTS) | v1 | Compatible with existing ZCode command files |
| Skills (SKILL.md with name and description frontmatter) | v1 | Same folder works on desktop ZCode and here |
| Plugin (`.zcode-plugin/plugin.json`) and marketplace | v1 | Local and GitHub sources; official CDN with sha256 verification is roadmap |
| Hooks for 7 events (stdin/stdout protocol) | v1 | |
| MCP: stdio, HTTP, SSE | v1 | stdio runs in the app shell or Termux |
| Subagents: general-purpose, Explore, custom markdown | v1 | |
| Project memory (MEMORY.md plus fact files) | v1 | Compatible format |
| Automatic context compaction plus /compact | v1 | |
| Model picker with thinking effort (low, high, max) | v1 | |
| Sessions: history, resume, fork | v1 | |
| Global and workspace AGENTS.md injection | v1 | |
| Built-in terminal (PTY) | v1 | See [terminal.md](terminal.md) |
| Termux integration (RUN_COMMAND) | v1 | |
| Dark theme with ZCode tokens; light theme | v1 | Dark is the default |
| File tree with change markers | v1 | |
| Queued messages while the agent is busy | v1 | |
| Image attachments to prompts | v1 | Routed to the multimodal model |
| Search within the conversation | v1 | |
| Simple usage view (tokens per session and model) | v1 | |
| Update check via the GitHub Releases API | v1 | |
| Full browser use automation (element picker) | Roadmap | v1 ships WebFetch plus a WebView viewer |
| Git graph and git markers backed by a git binary | Roadmap | Available through Termux mode when installed |
| Full goal mode (/goal verification loop) | Roadmap | v1 ships simple /goal storage in context |
| Edit history, undo and redo of replies | Roadmap | |
| Side conversations (/side, /btw) | Roadmap | |
| Repo wiki (Mermaid) | Roadmap | |
| Dynamic workflows (/workflow with a TypeScript runtime) | Roadmap | Complex; needs a TS evaluator |
| Scheduled automations | Roadmap | Constrained by Android background execution rules |
| Tasks dashboard (groups, archive) | Roadmap | v1 ships a simple session list |
| Idle-time tasks | Out of scope | Requires Coding Plan account login |
| SSH, WSL, or Docker remote development | Out of scope | |
| Remote control (QR pairing to desktop) | Out of scope | The app is standalone |
| Bot channels (Telegram, WeChat, Feishu) | Out of scope | |
| Computer use | Out of scope | Desktop feature |
| OAuth login | Out of scope | API key only, see [ADR 0003](adr/0003-api-key-auth.md) |
| Output styles, custom keybindings | Out of scope | |

## Milestones

| ID | Content | Status |
| --- | --- | --- |
| M0 | Foundation: repository with documentation, Gradle scaffold with all modules, design tokens, green CI, release workflow | Done |
| M1 | Terminal: PTY via JNI, shell sessions, Terminal screen on the stock shell, streaming output, resize, NDK build on CI | Done |
| M2 | Chat core: LLM client with streaming, session storage, chat screen with the ZCode theme, API key onboarding, model picker | Done |
| M3 | Tools and permissions: full v1 tool set, permission modes with approval UI, todo widget, compaction, message queue | In progress (tools, agent loop, approvals, queue, and todo widget are implemented; compaction and remaining polish pending) |
| M4 | Extensibility: slash and custom commands, skills, plugins and marketplace, hooks, MCP, subagents, memory | Done (built-in and custom markdown commands with argument substitution and per command tool filters, skills, project memory, hooks for all 7 events, MCP over stdio, HTTP, and SSE, the Task subagent tool, and a plugin loader for GitHub and local sources) |
| M5 | Integration and polish: full Termux bridge, file explorer, usage view, in-app updates, simple /goal, cross-device QA, signed v1.0.0 release | In progress (Termux bridge with backend selection, file explorer with change markers, settings screen, session list with resume, fork, export, and delete, update check, and simple /goal are implemented; usage view, on-device QA, and the signed v1.0.0 release remain) |

Each milestone closes with green CI, the pre-push checklist from AGENTS.md passed, the related documentation updated, and an APK attached to a prerelease.
