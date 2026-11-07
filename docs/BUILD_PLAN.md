# OGABOT — BUILD PLAN (v2, Hermes-informed)
**Supersedes BUILD_PLAN v1. Phases 0-2 SHIPPED (see CHANGELOG). This document governs Phases 3-8.**

## Product rules (user mandate, updated 2026-11 after Hermes+GrokBot research)
1. **Bots CAN create other bots ON THE USER'S EXPLICIT REQUEST.** "Create a bot called X that does Y" said to any bot = the bot DRAFTS a complete bot spec (name, persona, tools, skills, plugins, connectors, routines, model) via the `propose_bot` tool -> **human approves in a wizard-style confirmation card** (every field visible + editable before commit) -> the bot is born with its drafted everything. Bots may NEVER create bots silently or autonomously: creation always requires a human approval card, exactly like any `ask`-tier tool. Drafting = a tool; COMMITTING = human-only.
2. **Bots CAN talk to each other when needed** - bounded messaging protocol (Hermes Bot Mode pattern): roster in system prompt, `message_bot` tool, fire-and-forget delivery with receipts, loop guard (max 4 hops, reply-to-reply off by default), silence tokens, attribution.
3. **Approval system = GrokBot Auto Review pattern**: every tool call passes review; tiers auto/ask/never per bot+tool; in-chat approval cards with Allow once / Always allow (saves Custom Rule) / Deny; risk warnings on cards; background cards expire after 30 min (action NOT taken); Custom Rules in plain language (Ask-first wins ties); outbound messages from routines wait as DRAFTS until human sends; text from webpages/emails never counts as user authorization (prompt-injection defense).

## Design sources
- Hermes Agent (NousResearch, MIT): Bot Mode, bounded memory + staged approvals, progressive-disclosure skills, routines-as-cron landing in the owning bot's chat, bot-to-bot messaging with loop guards, group-chat round caps, session search (FTS5), /learn, background self-review.
- Zafiro (MIT): okia engine, Chaquopy tooling, JSON-in-sandbox settings, approval flows, Liquid design tokens.
- xAI GrokBot: bots-as-contacts UX, cloud-computer persistence, routines/skills/connectors (we do it on-device).

---

## Phase 3 — Engine & Tools & Approvals (v0.3.0) "bots with hands"
- Vendor **okia** (MIT, from Zafiro repo libs/) into :engine — turn loop, streaming, ToolRegistry, hooks, conversation tree, SessionCodec
- Built-in Kotlin tools: http_fetch, web_read, notify, open_uri, memory (add/patch/remove, bounded stores Hermes-style), list_bots (roster read-only)
- **Chaquopy 3.11** in-app Python (requests+bs4 baked); custom python tool registry + hot-load
- Permission tiers per bot per tool: auto / ask / never; **ApprovalRequest cards** in chat + notification buttons (once/session/always/deny)
- **Audit log** (Room): every tool call — bot, tool, args, result, tier decision, duration
- Memory upgrade (Hermes pattern): per-bot bounded MEMORY.md (2200 chars) + shared USER.md (1375 chars, app-wide profile), frozen-snapshot injection, substring patch/remove, capacity error forcing consolidation, duplicate rejection, **write approval gate** (staged, reviewable)
- **Approval cards (GrokBot Auto Review)**: Allow once / Always allow (saves Custom Rule) / Deny; risk warnings; 30-min expiry on background cards; Custom Rules editor (plain language, Ask-first wins)
- **propose_bot tool**: bot drafts a complete bot spec -> human confirms in wizard-style card -> bot born with drafted tools/skills/memory (rule #1)
- *Exit test: Researcher completes live web research end-to-end; never-tier refused; ask-tier blocks until answered; user asks Oga to create a 'News Watcher' bot -> approval card -> bot appears with drafted persona+tools; audit log shows every call.*

## Phase 4 — Routines & Autonomy (v0.4.0) "bots that live"
- Scheduler: WorkManager periodic + AlarmManager exact + BootReceiver re-arm + Room EventQueue drain (catch-up-once)
- `schedule_routine` / `pause_routine` / `cancel_routine` tools (creation is NOT bot-gated — routines are fine; only BOT creation is forbidden to bots)
- Two-phase wake loop: task turn → result committed to the bot's canonical chat as an event → wake turn decides follow-ups (chain cap 2, token budgets)
- Per-bot notification channels; Routine Manager UI; quiet hours; run log (GrokBot ops: cap 50/bot, keep 20 runs history w/ failure reasons, Test button, >=5 min spacing, pause/resume, per-routine timezone)
- Outbound messages composed by routines wait as DRAFTS until human sends (GrokBot pattern)
- Event triggers: notification-listener (chosen apps), incoming email (OpenMail), webhook endpoint, charge/unlock
- Routines land results in the owning bot's chat (Hermes pattern confirmed)
- *Exit test: daily 08:00 brief survives reboot + force-stop; missed run caught up once; budget cap pauses a bot.*

## Phase 5 — Skills & Self-Extension (v0.5.0) "bots that grow"
- **Progressive-disclosure skills** (Hermes pattern): skills/<category>/<name>/SKILL.md + references/; skills_list (name+description) always in system prompt; skill_view loads on demand
- **skill_manage tool** for bots: create/patch/delete/write_file with advisory linter; **staged writes + Tool Lab approval** (diff view; approve/reject) — Hermes write_approval gate UX
- `/learn` equivalent: "remember this as a skill" turns the current conversation or a URL into a draft skill (knowledge-base pattern for large sources: lean SKILL.md + references/ files)
- **Plugin bundles**: zip / GitHub URL import (plugin.json + tools + skills + mcp.json), security scan (dangerous-pattern heuristics; quarantine)
- **Per-bot MCP servers**: servers.json per bot via okia MCP clients; connect UI; dynamic tool surfacing; keys in Keystore vault
- *Exit test: bot proposes a tool → diff → approve → runs same session; connect one remote MCP server and its tools appear without app update.*

## Phase 6 — Storage, Session Search & Termux (v0.6.0)
- All-files-access → per-bot workspaces /sdcard/Ogabot/<bot>/; scoped files tool; SAF grants
- **Session search (Hermes FTS5 pattern)**: all bot turns into per-bot SQLite FTS5; `session_search` tool; free cross-session recall vs bounded memory
- Background **self-review job** (post-turn, cheap-model option): suggests memory consolidation + drafts skills (staged, human-approved — never auto-applied)
- Termux bridge (optional): localhost HTTP /run + RUN_COMMAND bootstrap + Termux:API (tts/stt/camera/sms-gated)
- *Exit test: core passes with Termux force-stopped; session_search finds a fact from a week-old chat; bridge round-trips when Termux alive.*

## Phase 7 — Crew: Bot-to-Bot & Group Chats (v0.7.0)
- **message_bot tool** (Hermes message_agent pattern): fire-and-forget, typed failure reasons, outcome sentences, attribution prefix, constraints/expecting fields; the SENDING bot composes its own words
- Teammate roster (names + roles) injected into every bot's system prompt
- **Loop guard**: max 4 bot-to-bot hops per exchange; reply-to-reply disabled by default; repeat-message refusal; silence tokens ([SILENT])
- **Group channels**: 2-6 bots, one visible conversation, @mention routing (auto-router option via cheap model), **3 rounds max, 10 messages/send cap, bots may PASS**, needs-you badge on @user, Activity view
- Bots CANNOT create bots (rule #1 enforced); bot creation remains wizard-only
- Voice: SpeechRecognizer + TTS; Vision: image attachments; share-target; notification quick-reply
- *Exit test: Oga delegates research to Researcher via message_bot; Researcher replies; loop guard caps a runaway exchange; 3-bot channel completes a handoff task.*

## Phase 8 — Hardening & v1.0.0
- Security re-audit (tiers, path traversal, prompt-injection hardening on tool results, memory/skill content scanning)
- Reliability matrix; cold start < 2s; battery profiling; Routines stress
- Docs: README, user guide, OEM battery guide
- Signed v1.0.0 release

## Post-v1 backlog (parked)
- Deterministic blob avatars from bot name (Hermes style); AI-generated portraits
- execute_code Programmatic Tool Calling (collapse tool pipelines into one Python turn)
- /journey learning timeline; knowledge-base skills from books; external skill dirs
