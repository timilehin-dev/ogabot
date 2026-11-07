# Changelog

## v0.1.0 — Phase 1: model layer & streaming chat
- OpenAI-compatible SSE streaming client (OkHttp + okhttp-sse + kotlinx.serialization) in :core
- Provider setup screen with presets (Ollama cloud / OpenAI / OpenRouter); API key encrypted with Android Keystore AES-GCM
- Seeded bot "Oga" (persona persisted at files/bots/oga/bot.json)
- Chat UI: streaming bubbles, auto-scroll, retryable error states (401/404/429/network mapped to human messages)
- Thread persistence: JSON snapshot at files/oga_thread.json, restored on restart (Room index deferred to Phase 2 — meaningful once multiple threads exist)
- INTERNET permission added

## Phase 0 — EXIT TEST PASSED (2026-11-07)
- v0.0.1 APK built by CI (tag run, green in ~4 min)
- Published to GitHub Releases via PAT (asset: Ogabot-v0.0.1.apk, 8.7 MB)
- Installed manually from Downloads; app renders Compose hello screen — screenshot confirmed
- Install path learned: no root; py_install_apk needs su; sandbox pm/am blocked; Shizuku rish (uid 2000)
  verified working in Termux (~/rish, RISH_APPLICATION_ID=com.termux, Shizuku v13.6.0) — use `sh ~/rish/rish -c "pm install -r <apk>"`
- Note: new repos default GITHUB_TOKEN to read-only, so releases are created via PAT, not in-workflow
