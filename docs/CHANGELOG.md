# Changelog

## v0.1.1 — UI redesign (Zafiro-informed dark theme)
- Dark "liquid" palette: bg #16151A, container tiers (#1D1C22 / #2F2D32), 24dp bubbles
- User bubble = primary @ 18% alpha (Zafiro's Liquid Design System trick); assistant bubbles carry avatar
- Bundled Inter variable font (FontVariation weights 400-700) — replaces quirky system font
- Markdown rendering: headings, bullet/numbered lists, code blocks, bold/italic/inline code
- Bot avatar = gradient circle with initial (fixes broken emoji glyph on Android 11)
- Animated typing dots, empty state with suggestion chips, pill composer + gradient send button
- Settings entry (top-right) to re-configure provider without reinstall; dark system status bar

## v0.1.0 — Phase 1: model layer & streaming chat
- OpenAI-compatible SSE streaming client (OkHttp + okhttp-sse + kotlinx.serialization) in :core
- Provider setup screen with presets (Ollama cloud / OpenAI / OpenRouter); API key encrypted with Android Keystore AES-GCM
- Seeded bot "Oga" (persona persisted at files/bots/oga/bot.json)
- Chat UI: streaming bubbles, auto-scroll, retryable error states (401/404/429/network mapped to human messages)
- Thread persistence: JSON snapshot at files/oga_thread.json, restored on restart (Room index deferred to Phase 2)
- INTERNET permission added

## Phase 0 — EXIT TEST PASSED (2026-11-07)
- v0.0.1 APK built by CI (tag run, green in ~4 min); installed manually from Downloads; screenshot confirmed
- Shizuku rish (uid 2000) verified in Termux as non-root install path; releases created via PAT (GITHUB_TOKEN is read-only on new repos)
