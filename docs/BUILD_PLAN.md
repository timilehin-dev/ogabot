# OGABOT — BUILD PLAN (v1)
**The phase contract. Every phase ships a runnable APK. No phase is done until its exit test passes on the target phone (TECNO CAMON 17P, Android 11).**

## Ground rules
1. Each phase ends with: code pushed, CI green, APK installed on device, exit test executed and logged in `docs/CHANGELOG.md`.
2. Regression rule: all previous phases' exit tests must still pass before a phase is declared done.
3. No secrets in the repo. Keys live in the device vault or GitHub Secrets only.
4. App must remain fully functional WITHOUT Termux at every phase. Termux features are additive.
5. Target: minSdk 26, targetSdk 34, compileSdk 37, JDK 17, AGP 9.1.1, arm64-v8a.

---

## Phase 0 — Repo & CI Foundation
**Goal:** the build loop is alive before any features exist.
- GitHub repo `ogabot` created; docs pushed (BUILD_PLAN, ARCHITECTURE, INSTALL, DEV_SETUP, README stub)
- Gradle skeleton: 5 modules (:app, :engine, :bots, :tools, :core) — engine starts as thin placeholder (okia vendoring lands Phase 3)
- GitHub Actions workflow: on push → assembleDebug → APK artifact; on tag v* → signed release (keystore from GH Secrets)
- Hello-world Compose screen: "Ogabot" title + version

**Deliverable:** CI-built APK installed on the phone; app opens showing Ogabot screen.
**Exit test:** full loop proven — edit a string in Termux, commit, push, CI green, new APK on phone via py_install_apk, string visible in app.

## Phase 1 — Model Layer & Streaming Chat (v0.1 "one bot that talks")
**Goal:** a real chat against an OpenAI-compatible provider.
- OkHttp + kotlinx.serialization SSE client (OpenAI-compatible: endpoint, key, model, temperature)
- Provider config screen + vault storage (Keystore-encrypted); default preset: Ollama cloud
- One seeded bot ("Oga") with persona JSON; Compose chat screen with streaming text + markdown
- Conversation persistence: Room thread index + session snapshot files; restore on restart
- Error states: no network, provider error, bad key (visible, retryable)

**Deliverable:** streaming chat working on mobile data.
**Exit test:** 5-message conversation; force-kill app mid-stream; reopen — full history restored, streaming resumes working.

## Phase 2 — Bot Layer: Registry, Personas, Memory (v0.2)
**Goal:** many bots, each with its own everything (identity + memory first).
- BotRegistry: `bots/<id>/bot.json` (name, emoji, persona, model ref, tool grants stub)
- Bots Home: contacts-style list (avatar, status, last activity); New Bot wizard
- Per-bot memory: profile.md + facts.jsonl, injected into system prompt each turn
- Memory viewer/editor screen (human and bot-editable)
- Per-bot conversation threads (Room), thread switcher

**Deliverable:** create a second bot ("Researcher") in-app; independent threads and memories.
**Exit test:** two bots with different personas answer differently to the same prompt; a memory fact the bot saves persists and is re-injected in a fresh session.

## Phase 3 — Tool System & Approvals (v0.3 "bots with hands")
**Goal:** tools run for real, gated by permissions.
- Vendor `okia` (MIT) into :engine — turn loop, ToolRegistry, hooks, session codec
- Built-in Kotlin tools: http_fetch, notify, open_uri
- Chaquopy 3.11 + baked pip (requests, bs4); custom Python tool registry (JSON) + hot-load; port proven parsers (web_read, web_search) as Python tools
- Permission tiers auto/ask/never per tool per bot; ApprovalRequest flow: in-chat card + notification Approve/Decline; audit log (Room)
- Tool-chain rendering in chat (collapsible cards)

**Deliverable:** Oga performs a live research task (search → read → summarize with URLs) inside chat.
**Exit test:** an ask-tier tool call blocks execution until approved; deny returns polite refusal without execution; audit log shows every call with args+result; previous phases still green.

## Phase 4 — Routines & Autonomy (v0.4 "bots that live")
**Goal:** the always-on clock, zero Termux dependency.
- RoutineStore (Room) + scheduler: WorkManager periodic backbone + AlarmManager exact + BootReceiver re-arm
- EventQueue: fired-but-not-run events, drained on app start (catch-up-once, flagged late); quiet hours
- Built-in tools: schedule_routine / list_routines / pause_routine / cancel_routine
- Two-phase turn: TASK TURN → result committed to owner bot thread as event message → WAKE TURN (bot decides follow-ups); chain cap (default 2) + per-bot daily token budget
- Per-bot notification channels; run log + Routine Manager UI (next run, history, toggle)
- Long turns: expedited work → foreground service "Oga is working..."

**Deliverable:** a daily 08:00 brief routine fires on-device, result lands in the owning bot's thread, bot sends a wake-turn summary notification.
**Exit test:** reboot phone → routine still fires; force-stop app → next scheduled fire still works; a deliberately missed run is caught up once; budget cap pauses a bot and notifies.

## Phase 5 — Self-Extension (v0.5 "bots that grow")
**Goal:** bots extend themselves; every install human-approved (Dev Mode ON default).
- Tool Lab: bots propose Python tools (source diff screen) → approve → hot-load via Chaquopy; per-bot tools dir; publish-to-library
- Skills: SKILL.md authoring/editing (Zafiro-compatible + .seed SHA-256 sidecar); skill injection into prompts; skill library UI
- Plugin bundles: plugin.json + tools + skills + mcp.json; import via share-sheet zip or GitHub URL; manifest validation
- Per-bot MCP: servers.json (streamable HTTP via okia MCP clients), connect UI, dynamic tool surfacing, keys in vault

**Deliverable:** bot creates a tool through approval and uses it the same session; a public remote MCP server is connected and its tools are callable without app update.
**Exit test:** end-to-end self-extension: "build me a tool that X" → diff → approve → tool runs; connecting an MCP server exposes its tools to the bot; deny path leaves no artifacts.

## Phase 6 — Storage & Termux Deep Access (v0.6)
**Goal:** real filesystem + optional Linux depth.
- Storage wizard: All-files-access (MANAGE_EXTERNAL_STORAGE) + degraded sandbox-only mode
- Per-bot workspace `/sdcard/Ogabot/<bot_id>/`; files tool scoped: own workspace + sandbox read/write, /sdcard/Download read-only; SAF folder grants
- Termux bridge client (optional): localhost HTTP /run with X-Key, status page, bootstrap via RUN_COMMAND intent, Termux:API passthroughs (tts, stt, camera, sms — sms `never` by default)

**Deliverable:** bot writes a markdown report to its workspace visible in Files app; bridge tool executes a shell/python command when Termux is alive.
**Exit test:** force-stop Termux → every core feature (chat, tools, routines, extension) still passes Phase 1–5 exit tests; with Termux: bridge round-trip works; no bridge = clean, explained failure.

## Phase 7 — Crew, Voice, Vision, Polish (v0.7)
**Goal:** the GrokBot-grade experience.
- Channels: multi-bot group threads, @mention routing, task handoff between bots
- Conversation branching UI (regenerate from any node — okia tree)
- Voice: SpeechRecognizer input + TTS output toggle per bot; image attachments → vision models
- Share-target (text/link/image into a bot chat); quick-reply from notifications (RemoteInput)
- Backup/export (bots+memory+skills zip); cost/token dashboard; audit viewer UI

**Deliverable:** two bots complete a two-step job in one channel; voice round-trip; image Q&A.
**Exit test:** scripted end-to-end scenario: share a link → bot summarizes → second bot reviews → user approves final draft via quick-reply.

## Phase 8 — Hardening & v1.0 Release
**Goal:** shippable.
- Security review: tier matrix re-audit, path traversal checks, prompt-injection hardening for tool outputs, key masking
- Reliability: no-network/quota/provider-failure matrix; cold start < 2s; battery profiling of routines
- Docs: README, user guide, troubleshooting (OEM battery sections)
- Tag v1.0.0 → signed GitHub Release

**Deliverable:** v1.0.0 signed APK on GitHub Releases; fresh-install wizard validated end to end.
**Exit test:** install from Releases page on a clean profile → wizard → first bot → chat → routine scheduled and firing. 
