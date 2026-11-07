# Changelog

## v0.0.1 — Phase 0: Repo & CI foundation
- Gradle skeleton: :app (Compose hello world) + :engine, :bots, :tools, :core placeholders
- GitHub Actions: push → debug APK artifact; tag v* → APK attached to Release
- Docs seeded: BUILD_PLAN / ARCHITECTURE / INSTALL / DEV_SETUP
- Note: AGP 8.7.3 + Kotlin 2.0.21 + Gradle 8.9 for a high-probability first green build;
  upgrade to AGP 9.x (Zafiro parity) planned for Phase 3 when okia is vendored.

## Phase 0 — EXIT TEST PASSED (2026-11-07)
- v0.0.1 APK built by CI (tag run, green in ~4 min)
- Published to GitHub Releases via PAT (asset: Ogabot-v0.0.1.apk, 8.7 MB)
- Installed manually from Downloads; app renders Compose hello screen — screenshot confirmed
- Install path learned: no root; py_install_apk needs su; sandbox pm/am blocked; Shizuku rish (uid 2000)
  verified working in Termux (~/rish, RISH_APPLICATION_ID=com.termux, Shizuku v13.6.0) — use `sh ~/rish/rish -c "pm install -r <apk>"`
- Note: new repos default GITHUB_TOKEN to read-only, so releases are created via PAT, not in-workflow
