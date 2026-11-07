# Changelog

## v0.2.0 — Phase 2: Bots Home (multi-bot layer)
- BotRegistry: bots/<id>/bot.json; Bots Home contact list; New Bot wizard (name/persona/gradient)
- Per-bot threads (threads/<botId>.json; Phase 1 oga_thread.json auto-migrated)
- Per-bot memory: memory/profile.md injected into every turn; editable via Memory screen (chat header)
- STABLE SIGNING: own keystore in GitHub Secrets signs debug+release — updates now install over previous versions (one-time uninstall for this release)
- Back navigation (hardware back → Home), provider banner on Home when unset

## v0.1.1 — UI redesign (Zafiro-informed dark theme)
- Dark palette, Inter variable font, markdown rendering, gradient avatars, typing dots, suggestion chips, pill composer, Settings entry

## v0.1.0 — Phase 1: model layer & streaming chat
- OpenAI-compatible SSE client in :core; provider setup (Ollama cloud/OpenAI/OpenRouter presets); Keystore-encrypted API key; streaming chat; JSON thread persistence; retryable error states

## Phase 0 — EXIT TEST PASSED (2026-11-07)
- CI hello-world loop verified end-to-end; Shizuku rish = non-root install path; releases via PAT
