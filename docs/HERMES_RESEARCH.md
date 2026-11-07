# Hermes Agent — Research Notes (for Ogabot)
Source: NousResearch/hermes-agent (MIT) — site docs, Bot Mode docs, README. Fetched 2026-11-07.

## What Hermes is
Self-hosted personal agent (CLI + Desktop + 20-platform messaging gateway). Core primitive = **profiles** (~/.hermes/profiles/<name>/ with isolated config, memory, skills, credentials, chats). Bot Mode is a UI over profiles: a roster of named bots.

## Features we copy into Ogabot
1. **Bot = profile** (validates our bots/<id>/ design)
2. **Canonical forever-chat** per bot; /new reroutes to compact
3. **Bounded memory**: MEMORY.md (2200c) + USER.md (1375c), frozen-snapshot injection, memory tool (add/patch/remove + substring match), capacity errors force consolidation, duplicate rejection, write-approval gate with staged writes
4. **Progressive-disclosure skills**: skills_list in prompt (~3k tok), skill_view on demand; SKILL.md format; skill_manage tool + staged diffs (Tool Lab)
5. **Learning loop**: background post-turn review (cheap model), /learn, knowledge-base skills
6. **Routines = cron namespaced [bot:<name>]**, results land in the owning bot's chat
7. **message_agent**: fire-and-forget bot-to-bot DMs, receipts, typed failure reasons, roster in system prompt, loop guard (max_hops 4, no reply-to-reply, repeat refusal), silence tokens
8. **Group chats**: 2-6 bots, @mention routing (auto-router via cheap model), 3 rounds max, 10 msgs/send, bots may PASS, needs-you badge
9. **Session search**: SQLite FTS5, free cross-session recall
10. **Security**: skill/memory content scanning, quarantine, approval buttons (once/session/always/deny)

## Hard rules for Ogabot (user mandate)
- Bots CANNOT create bots (wizard-only, no tool pathway)
- Bots CAN message each other via bounded message_bot protocol

## Skipped deliberately
- Messaging-gateway platforms (Telegram etc.) — the phone IS the surface
- Cloud backends (Docker/SSH/Modal sandboxes) — Termux bridge is our depth path
