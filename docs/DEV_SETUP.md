# OGABOT — DEV SETUP (this phone, no PC)
## Repo & auth
- Repo: github.com/<user>/ogabot (public; free Actions)
- Auth: GitHub fine-grained PAT → stored in Zafiro vault (`ogabot_github_pat`), used by git push only. Scope: Contents Read/Write on ogabot repo, Metadata Read.
- Working copy: Termux `~/ogabot` (full git). Zafiro stages files into Termux via base64 chunks (verify sha256 per file), then commits.

## The loop
1. Edit/build locally in Zafiro workspace → stage to Termux → `git add/commit/push`.
2. GitHub Actions: push → assembleDebug APK artifact; tag v* → signed release.
3. Install: `py_install_apk` on the artifact/release URL → app lands on phone.
4. Verify phase exit test → update docs/CHANGELOG.md → next phase.

## CI (release.yml, cloned from Zafiro pattern)
- setup JDK 17 + Python 3.11 (Chaquopy) + Android SDK + Gradle
- keystore: base64 in GH Secrets, decoded at build; signing props written at build time
- triggers: push tags v* → GitHub Release with signed APK; workflow_dispatch → artifact only

## Conventions
- Kotlin official style; MVI; one PR-equivalent commit set per phase; CI must stay green; CHANGELOG.md updated per phase with exit-test results.
