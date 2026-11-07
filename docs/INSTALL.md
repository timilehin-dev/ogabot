# OGABOT — INSTALLATION (end users)
## Requirements
- Android 8.0+ (best on 11+); ~120 MB free; internet (mobile data fine)
- An OpenAI-compatible API key (default preset: Ollama cloud; also OpenAI/OpenRouter/Groq/DeepSeek)

## Install
1. Get the APK: GitHub Releases page (or a direct artifact link) → tap → allow install-from-source.
2. Open Ogabot → first-run wizard:
   a. **Model**: paste endpoint + key + pick model (or choose the Ollama cloud preset). Key is stored encrypted on-device.
   b. **Storage** (recommended): grant All files access so bots get their own folders in `/sdcard/Ogabot/<bot>/`. Skip = sandbox-only degraded mode.
   c. **Notifications**: allow (routine results and approvals arrive here).
   d. **Battery**: follow the OEM screen — disable battery optimization for Ogabot; on Tecno/Infinix/XOS also enable **Autostart**. This is what keeps routines reliable.
3. Create your first bot (name, emoji, persona) → chat.
4. Routines: Bot Settings → Routines → add (e.g., Daily 08:00 brief). No Termux needed.

## Updating
Install the newer APK over the old one (data preserved). Backup: Bot Settings → Export (zip of bots+memory+skills).

## Uninstall behavior
App sandbox is removed by Android. `/sdcard/Ogabot/` workspace files persist — delete manually if unwanted.

## Troubleshooting
- Routines not firing → check battery optimization + XOS autostart; open app once after reboot.
- Bridge/Termux tools missing → Termux not running or bridge not installed (Settings → Termux → status).
