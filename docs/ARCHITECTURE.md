# OGABOT — ARCHITECTURE
A native Android host for autonomous personal AI agents ("bots as contacts"). Pattern source: niki914/zafiro (MIT) — okia engine, Chaquopy tooling, JSON-in-sandbox settings. Multi-agent layer is original.

## Modules
- `:app` — Compose UI (MVI): BotsHome, ChatThread, Channels, BotSettings, Routine Manager, Tool Lab, Providers, Storage/Permissions
- `:engine` — vendored okia: AgentLoop turn loop, streaming ProtocolEvents, ToolRegistry, MCP clients, conversation tree, SessionCodec
- `:bots` — BotRegistry, MemoryStore, RoutineScheduler, EventQueue, TurnOrchestrator, ApprovalQueue, ExtensionManager (tools/skills/plugins/MCP)
- `:tools` — built-in Kotlin tools, Chaquopy host + custom python tools, Termux bridge client (optional)
- `:core` — storage (FileStore+Room), vault (Keystore), notifications (per-bot channels), permissions, HTTP client, settings

Single process. Foreground services only during active turns/routines. No Binder IPC surface in v1.

## Storage layout
```
sandbox/files/
  settings/            app_state.json, providers.json (agent-editable JSON)
  bots/<id>/
    bot.json           persona, model ref, tool grants, dev_mode, budget
    routines.json      routine definitions
    memory/            profile.md, facts.jsonl, summaries/
    tools/             bot-authored python tools (registry.json)
    skills/            SKILL.md + .seed sidecars
    mcp.json           per-bot MCP servers
  sessions/<thread>.json   okia session snapshots
  vault.bin            Keystore-wrapped secrets

/sdcard/Ogabot/<bot_id>/   workspace (All-files-access tier): docs, downloads, artifacts
```
Room DB: thread index, message blocks index, event_queue, run_log, audit_log.

## Key flows
1. **Chat turn:** UI → TurnOrchestrator → okia LoopRequest{history+memory inject, LLM cfg, ToolRegistry(bot-filtered)} → SSE stream → ProtocolEvents → UI; ToolCallReady → permission tier check → (ask: ApprovalRequest card/notif) → execute → ToolResult committed → loop to Stop.
2. **Routine wake loop (no Termux):** Scheduler (WorkManager/Alarm/Boot re-arm) → EventQueue → expedited worker → foreground svc → PHASE 1 task turn → result committed to owner thread as event message → PHASE 2 wake turn (bot decides: notify/memory/follow-up/silence). Chain cap 2, daily token budget, catch-up-once, quiet hours.
3. **Approval flow:** tier never = refused; ask = ApprovalRequest (in-chat card + notification buttons, timeout = deny); auto = execute + audit.
4. **Self-extension:** bot proposes artifact (tool/skill/plugin/mcp) → Tool Lab diff → approve → written to sandbox → hot-registered (python via Chaquopy; skills via loader; MCP via client) → auditable. Dev Mode default ON; installs always approved; "fast lane" auto-approve reserved for sandbox+network-only tools (later opt-in).
5. **Termux (optional):** bridge status check → localhost HTTP /run (X-Key) → stdout capture; bootstrap/repair via com.termux RUN_COMMAND; hardware via Termux:API. Absent Termux = feature hidden, core unaffected.

## Permission model (defaults)
| Tier | Examples | Default |
|---|---|---|
| never | payments, SMS send, contacts mutation, deletions, phone-use | blocked |
| ask | storage outside own workspace, notifications, MCP calls, termux bridge, camera | approval each time |
| auto | sandbox reads/writes, own workspace files, network reads (http_fetch/web_read), memory writes | execute + audit |

## Non-goals (v1)
Payments/transactions; cloud computer provisioning; multi-process IPC; Xposed; floating overlay.
