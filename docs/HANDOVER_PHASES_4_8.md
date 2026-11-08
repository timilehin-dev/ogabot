# OGABOT — PHASES 4–8 HANDOVER BRIEF
**From:** Zafiro (on-device build agent) · **To:** Laptop-based agent taking over development
**Date:** 2026-11-08 · **Repo:** github.com/timilehin-dev/ogabot (public, MIT)
**Status at handover:** v0.4.0-beta5 — Phase 4 functionally assembled but tool calling is NOT working reliably on-device. User paused the build here.

---

## 0. READ THIS FIRST — THE SITUATION

### What the user wants (product truth, don't lose this)
1. **A native Android app like xAI's GrokBot**: bots-as-contacts, EACH bot with its own persona, memory, tools, skills, routines, model config. The phone IS the computer (no cloud computer).
2. **Model-agnostic**: must work with ANY OpenAI-compatible endpoint/model. Never assume or tune for one model family.
3. **Bots may create other bots ONLY on the user's explicit request** (propose_bot → human approval card → commit). Never autonomous.
4. **Bots can talk to each other** via a bounded messaging protocol (roster in system prompt, message_bot tool, loop guard max 4 hops, silence tokens).
5. **User never sees raw tool syntax** in chat — only "⚙️ tool-name" / "✅/❌ tool-name" status lines.
6. Approval system = GrokBot Auto Review pattern (tiers auto/ask/never, Allow once / Always allow → Custom Rules / Deny, background cards expire 30 min).
7. App must fully work WITHOUT Termux; Termux = optional deep-access enhancer.

### The live problem (why the handover)
Tool calling fails on-device across FOUR model output formats we've now seen:
1. JSON array in text: `[{"call":"json_api","args":{...}}]`
2. Native Hermes tokens: `<|tool_call_start|>[json_api(url='...')]<|tool_call_end|>`
3. Prose pseudo-call: `[Tool call] json_api {"url":"..."}`
4. MS dots style: `<dots_function_call><invoke name="json_api">{"url":...}</invoke></dots_function_call>`

v0.4.0-beta4/beta5 added OpenAI function calling as primary (tools=[...] in request, structured tool_calls deltas). The code compiles and the protocol is correct, but the user's provider/model STILL returns prose-format calls — the FC path isn't engaging. Suspected causes (unverified — DEBUG FIRST):
- (a) The user's provider ignores/strips the `tools` request param (some Ollama-cloud models & proxies do).
- (b) The stream parser misses `tool_calls` deltas for that provider's chunk shape (e.g. full tool_calls object in ONE chunk vs. fragmented deltas; or delta inside `choices[0].message` for non-stream responses).
- (c) The provider returns 200 but silently drops unknown fields (no error, no tools).
**First action for the new agent: capture the actual HTTP exchange.** Add a debug toggle (Settings → Developer → "Log raw API traffic") writing request JSON + raw SSE lines to a file, then re-test with the user's endpoint. Diagnose from evidence, not more format-guessing.

### Architecture reality check
- `:core/OpenAiClient.kt` — hand-rolled OkHttp SSE client. FC support added in beta4/5 but stream parsing may be incomplete (see suspects above). **The vendored `okia` engine (in `:engine`, compiles green, currently UNUSED) already has a battle-tested OpenAI protocol implementation (`protocol/OpenAIChatCompletionProtocol.kt`) + proper turn loop (`loop/RealAgentLoop.kt`). THE STRATEGIC FIX: finish wiring bots onto okia instead of patching OpenAiClient.** okia speaks the protocol layer natively (FC + streaming + tool registry + hooks). This was always the Phase 3 plan; time pressure made us ship the simpler client. Now it's the blocker.
- `:bots`, `:tools` modules are still empty placeholders. All logic lives in `:app` (VM) + `:core`.

---

## 1. CURRENT STATE INVENTORY (what exists and works)

### Shipped releases (all install-over, stable signing via GH Secrets)
| Tag | What works on-device |
|---|---|
| v0.1.0 | Streaming chat, provider setup (endpoint/key/model + presets Ollama cloud/OpenAI/OpenRouter), Keystore-encrypted key, thread persistence (JSON snapshot), error states |
| v0.1.1 | Dark design system (Zafiro Liquid-informed), Inter variable font, markdown renderer (headings/bullets/numbered/code/inline), gradient avatars, typing dots, suggestion chips |
| v0.2.0/0.2.1 | Bots Home (contact list), New Bot wizard, per-bot threads + memory editor, bot settings (rename/persona/avatar/delete), stack navigation, onboarding flow |
| v0.3.0-beta3 | Tools: http_fetch, web_read, notify, open_uri, memory_write, list_bots, propose_bot; approval cards; audit log (JSONL) + Audit screen; ToolCallParser (multi-format fallback) |
| v0.4.0-beta5 | Routines (plain-language schedule parser, dual-layer scheduler: AlarmManager exact + 15-min WorkManager heartbeat + BootReceiver, two-phase wake loop worker, per-bot notification channels, Routines UI); Chaquopy Python 3.11 runtime (json_api/page_text/text_stats starters, registry files/python_tools.json, contract `main(d: dict) -> str`); TermuxBridge client (zbridge pattern, clean failure); shell/schedule_routine/list_routines tools; **OpenAI FC as primary tool path (possibly not engaging — see §0)** |

### Key files (repo layout)
```
app/src/main/java/io/github/timilehin_dev/ogabot/
  MainActivity.kt          # entry + OgabotViewModel (nav stack, chat loop, approvals, routines)
  Scheduler.kt             # AlarmManager + WorkManager heartbeat + receivers
  RoutineRunWorker.kt      # two-phase wake loop (runs via WorkManager)
  BuiltinToolsCompat.kt    # background-safe tool execution for workers + BotNotifier
  ui/ (Theme.kt Markdown.kt ChatScreen.kt HomeScreen.kt WizardScreen.kt
       SetupScreen.kt MemoryScreen.kt RoutinesScreen.kt AuditScreen.kt BotSettingsScreen.kt)
core/src/main/java/io/github/timilehin_dev/ogabot/core/
  Models.kt (DTOs incl. FC types) · OpenAiClient.kt (SSE + FC) · ToolCallParser.kt (4-format fallback)
  BuiltinTools.kt (tool impls + python dispatch) · PythonTools.kt (Chaquopy host) · TermuxBridge.kt
  Registry.kt (BotPersona/BotRegistry) · MemoryStore.kt · ChatStore.kt · Routines.kt (RoutineStore)
  ApprovalGate.kt · AuditLog.kt · VaultStore.kt (Keystore AES-GCM) · BotSeed.kt · Tools.kt
engine/src/main/java/com/niki914/okia/   # VENDORED OKIA (63 files, compiles, UNUSED)
docs/  BUILD_PLAN.md (v2.1) HERMES_RESEARCH.md GROKBOT_RESEARCH.md CHANGELOG.md
.github/workflows/build.yml  # push→artifact; tag v*→(release created via PAT, see §3)
```

### Data on device (sandbox files/)
- `bots/<id>/bot.json` (+ toolGrants map), `bots/<id>/memory/profile.md`, `memory/facts.jsonl`
- `threads/<botId>.json`, `routines/<botId>.json`, `audit/<yyyy-MM>.jsonl`
- `oga_thread.json` (legacy, migrated), `custom_rules.json`, `vault_provider.bin`, `python_tools.json`, `python/*.py`

---

## 2. BUILD & TEST LOOP (proven, reuse it)
1. Edit code locally (any machine), git push to main (commits trigger CI; concurrency-cancels stale runs).
2. GitHub Actions builds assembleDebug (~4 min warm; Chaquopy adds a few min). Artifact `Ogabot-debug`.
3. **Releases are created via the owner's fine-grained PAT (NOT GITHUB_TOKEN — new repos default it read-only).** Flow: create git tag → POST /releases → upload asset. PAT lives with the owner (vault: ogabot_github_pat); ask the owner if you need it, or have the owner create releases.
4. Owner installs APK on a TECNO CAMON 17P (Android 11, arm64-v8a). Signing keystore in GH Secrets (OGABOT_KEYSTORE_BASE64/PASSWORD/ALIAS/KEY_PASSWORD) — all updates install over each other; NEVER rotate it.
5. Exit-test discipline: every phase ends with a real on-device test + CHANGELOG entry. Regression rule: previous phases' tests must keep passing.

---

## 3. PHASE 4 COMPLETION (finish before anything else)

**Step 1 — Diagnose the tool-calling failure with evidence (1–2 days)**
- Add Settings → Developer → "Log API traffic" (raw request JSON + raw SSE chunks to files/api_log.txt). Ship as v0.4.0-beta6, have the owner re-run the stars test with their provider, pull the log.
- Decide from evidence:
  - Provider ignores `tools` → implement capability detection: on first turn send tools; if response contains no tool_calls AND provider documentation/behavior says unsupported, mark bot config `fc_capable=false`; for non-FC models show a one-time notice "This model doesn't support tools" and disable tool prompts entirely (do NOT fall back to text formats — that's the road we're leaving).
  - Parser miss → fix delta accumulation for that chunk shape (consider: some providers send complete `tool_calls` in one delta; some send `choices[0].message.tool_calls` on a final non-delta chunk when stream=true; some need `stream_options: {"include_usage": true}` handling).
  - okia shortcut: port the FC-streaming test cases from okia's `protocol/OpenAIChatCompletionProtocol.kt` into OpenAiClient, or accelerate §4 (okia wiring) which obsoletes OpenAiClient.

**Step 2 — UX verification**: bot calls tool → chat shows `⚙️ json_api` → `✅ json_api` → model's answer. No raw syntax anywhere (sanitizeDisplay covers 4 formats + invoke-without-wrapper).

**Step 3 — Routine exit tests**: schedule "every day at HH:MM" ~2 min out → fires without Termux → result in bot's chat as `⛏ Routine:` message → per-bot notification arrives → REBOOT phone → still fires (BootReceiver) → force-stop app → next heartbeat catches up. Missed-run catch-up-once; 50-routine cap.

**Step 4 — polish**: per-bot daily token budget (soft cap → pause + notify), quiet hours, "next run" preview in RoutinesScreen, token-cost per run in run log. Then tag **v0.4.0 final**.

---

## 4. PHASE 5 — OKIA WIRING + SKILLS + SELF-EXTENSION (v0.5.0)

**5A. Wire bots onto okia (the structural fix).**
- Replace `OpenAiClient` usage in VM + RoutineRunWorker with okia sessions: one `Okia` instance per bot (okia supports multi-session; see `RealOkia.kt`, `Okia.kt`, `loop/RealAgentLoop.kt`).
- ToolRegistry: register BuiltinTools + PythonTools as okia tools (`tooling/ToolRegistry.kt`, `ToolExecutor`). Approval gating lands as okia hooks (`hooks/ToolCallHolder.kt` beforeToolCall → ApprovalGate).
- Conversations: adopt okia's conversation tree (`conversation/`) with SessionCodec persistence — replaces ChatStore JSON snapshots. Enable branching ("regenerate from here") in UI.
- Benefits unlocked: native FC handling (correct across providers), repetition detection, context compaction, hooks, MCP clients (already in okia: `mcp/`), test fakes.
- Keep `OpenAiClient` only as a diagnostic fallback until removed.

**5B. Skills system (Hermes progressive disclosure).**
- `bots/<id>/skills/<category>/<name>/SKILL.md` (+ references/). Frontmatter: name, description (≤60c), version, tags.
- `skills_list` (name+description index, ~3k tokens) ALWAYS in system prompt; `skill_view(name)` tool loads full content on demand; `skill_view(name, path)` for reference files.
- `skill_manage` tool for bots: create/patch/delete/write_file → **staged writes** (`pending/skills/`) → Tool Lab screen (unified diff, approve/reject) — mirrors memory write-approval. Linter advisories: body ≤24k chars, no incident-log shapes.
- **Teach-a-task (conversation mode)**: "save that as a skill" → drafts SKILL.md from the just-finished conversation (task, steps bot took, corrections) → staged approval. (GrokBot parity.)
- Shared library + per-bot enablement; publish/export to zip (post-v1: GitHub tap imports).

**5C. Python Tool Lab.**
- Bot-authored tools: propose python file + registry entry → Tool Lab diff (it's code) → approve → hot-load into `python_tools.json` (Chaquopy hot reload works).
- Security: static scan (dangerous patterns: os.system, subprocess to binaries, network-exfil heuristics) before staging; approved tools run with Chaquopy's app sandbox.

**5D. Per-bot MCP servers.**
- `bots/<id>/mcp.json` → okia MCP clients (streamable HTTP, discovery). Connect UI (URL + auth header → Keystore vault). Dynamic tool surfacing into the bot's ToolRegistry with tier=ask default.
- Exit test: connect a public MCP server (e.g. deepwiki) → its tools appear + execute without app update.

**5E. Bot templates marketplace.** Starter bots (Morning Briefer, Inbox Sorter, Research Desk, Ops Watcher) + "Team preset" (Coordinator + Researcher + Reviewer-that-cannot-approve-its-own-work). propose_bot gains full drafting: tools, skills, connectors, routines (all visible in the confirmation card).
Exit test (phase gate): "build me a tool that X" → diff → approve → runs same session; connect one MCP server; "save that as a skill" yields approved skill; create a bot from a template.

---

## 5. PHASE 6 — STORAGE, SESSION SEARCH, TERMUX (v0.6.0)
- All-files-access wizard (MANAGE_EXTERNAL_STORAGE) + degraded sandbox mode. Per-bot workspaces `/sdcard/Ogabot/<bot>/`; `files` tool scoped (own workspace rw, /sdcard/Download ro); SAF folder grants.
- **Session search**: per-bot SQLite FTS5 over all turns; `session_search` tool (discovery/scroll/browse shapes like Hermes). Memory stays bounded; history stays searchable.
- **Background self-review** (post-turn, cheap-model configurable): proposes memory consolidations + drafts skills — ALWAYS staged, never auto-applied. `/journey`-style learning timeline (list view fine for v1).
- Termux deep access: bridge status page, RUN_COMMAND bootstrap (install/start bridge + cron self-heal), Termux:API passthroughs (tts, stt, camera, battery, location; sms = never by default). Read-only mode toggle. Core must pass all tests with Termux force-stopped.

---

## 6. PHASE 7 — CREW: BOT-TO-BOT + GROUPS + VOICE/VISION (v0.7.0)
- `message_bot` tool (Hermes message_agent pattern): fire-and-forget + delivery receipt + typed failure reasons + outcome sentence; attribution "Message from 🤖 Name (@handle)"; constraints/expecting fields; the SENDING bot composes its own words (never verbatim forward). Roster (names+roles) injected into every bot's system prompt.
- Loop guard: max 4 bot-to-bot hops per exchange; reply-to-reply OFF by default; repeat-to-same-target refusal; silence tokens ([SILENT], NO_REPLY). Delivery = enqueue a wake turn in the target bot's session.
- **Group channels**: 2–6 bots, one visible conversation, per-member persistent room sessions; @mention routing (auto-router via cheap model call over room purpose + roles + last msgs — okia can do this as a side call); **max 3 rounds / 10 messages per send; bots may PASS**; needs-you badge on @user; Activity view; threads (post-v1 ok).
- Voice: Android SpeechRecognizer input + TTS output per bot. Vision: image attachments → vision-capable models (Ollama cloud verified).
- Share-target (text/link/image into a bot chat), notification quick-reply (RemoteInput), bot templates polish.
Exit test: Oga delegates research to Researcher via message_bot; Researcher replies; runaway exchange hits the 4-hop cap; 3-bot channel completes a handoff with one PASS.

---

## 7. PHASE 8 — HARDENING & v1.0.0
- Security re-audit: tier matrix, path traversal (files tool), prompt-injection hardening for tool outputs (wrap results, treat as data not instructions), memory/skill content scanning (Hermes pattern), key masking everywhere.
- Reliability matrix (no-network / quota / provider 4xx-5xx / malformed JSON args), cold start <2s, routine battery profiling (target: <3%/day with 3 daily routines).
- Backup/export: zip of bots+memory+skills+threads (Settings), restore flow.
- Docs: README with screenshots, user guide, OEM battery guide (XOS autostart!), troubleshooting. CHANGELOG final.
- Tag **v1.0.0** → GitHub Release (signed, via PAT) → fresh-install wizard end-to-end test on clean profile.

---

## 8. GOTCHAS THAT COST US HOURS (don't repeat)
- Kotlin/Gradle: Kotlin 2.2.0 required by okia (kotlin.time.Clock); compose+serialization plugins must match Kotlin version. Chaquopy REQUIRES `ndk.abiFilters` (arm64-v8a) in EVERY module applying the plugin (app AND core). `jvmTarget` deprecation warnings are benign.
- kotlinx-serialization-json must be an explicit dep in :app (core's `implementation` doesn't expose it).
- Material icons-core lacks Description/List-as-FQN (clash with kotlin.collections.List) → use TextButtons.
- `return@runBlocking` inside `repeat{}` returns from the WRONG lambda — use for+break inside one runBlocking. Suspend calls need suspend context.
- @Serializable without import = misleading "internal in file" error. Smart-cast fails on Pair.first — destructure.
- GitHub: fine-grained PAT can't use git/trees API (403) → push per-file via Contents API (sha race → retry on 409); workflow-file pushes need Workflows permission; GITHUB_TOKEN on new repos is read-only → releases via PAT; client-side timeouts on big uploads can be lies — GET /releases/tags/<t> to verify before retrying.
- Termux on the build phone is flaky under backgrounding (SSH wrapper wedges on slow commands) — prefer API-verification over reading Termux output; scripts via b64 chunks + nohup; verify via API.
- User's provider quirk: unknown endpoint/model — evidence first (§3 Step 1), no more format guessing.

## 9. DEFINITION OF DONE (per phase)
Code pushed → CI green → APK released (PAT flow) → owner installs → on-device exit test passes (logged in CHANGELOG) → previous phases' tests still pass → user confirms.
