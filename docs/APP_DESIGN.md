# OGABOT — APP DESIGN

**Status:** Proposal. Companion to `ARCHITECTURE_V3.md` (technical architecture).
**Date:** 2026-11-08
**Reference:** Grok Bot (xAI) — bots-as-contacts, presence as interface, the Bot's own computer, inline cards, routines in the transcript. Adapted to Android, where **the phone is the computer**.

---

## 1. Design thesis

Ogabot is a **messaging app for persistent AI teammates** — not a dashboard, not a chatbot, not a workflow builder.

The main objects are **Bots, not conversations.** A Bot has a name, an avatar, a title, a memory, a set of tools, a schedule, and its own workspace on the phone. When you come back tomorrow, you come back to the same Bot.

The phone is the Bot's computer. A Bot can read your notifications, send an SMS, run a shell command, write a Python script in Termux, and file the result in its workspace — and it stops to ask before anything consequential.

The home screen answers **"what do I need to know and what do I need to decide"** — and then gets out of the way.

---

## 2. Design principles

From GrokBot's design language, adapted to Android:

1. **Bots are the object.** The roster is the home. Conversations are views of a Bot.
2. **Presence is interface.** The avatar and title show what the Bot is doing — idle, working, waiting, needs help — without opening the chat.
3. **Chat is the main interface.** Everything else is a detail or a configuration surface.
4. **Structured UI where prose doesn't fit.** Approval cards, routine cards, tool-call cards, skill diffs — inline in the transcript.
5. **Their computer, not yours.** The Bot works on the phone; you watch, preview, and take over only when needed.
6. **Honest states.** Offline, provider errors, blocked capabilities, skipped routines — shown as they are, never hidden.
7. **One primary focus per view.** No marketing dashboards, no artificial metrics.
8. **Quiet, precise visual language.** Near-black surfaces, subtle separators, bright bot avatars.
9. **The human is the only authorizer.** Every consequential action stops for a human.
10. **Standard Android patterns.** Bottom navigation, Material 3, familiar gestures — not a custom invention.

---

## 3. Information architecture

```
Bots (home)
├── Needs you                    <- approvals, drafts, failures, escalations
├── Groups                       <- 2-6 bots, one conversation
├── Pinned bots
└── All bots                     <- avatar, name, title, presence, unread
│
Bot detail
├── Chat                         <- the main interface; transcript + inline cards
├── Routines                     <- schedule, next run, run history
├── Memory                       <- MEMORY.md + USER.md, editable
├── Workspace                    <- the bot's files on the phone
└── Settings                     <- persona, model, tools, connectors, budget
│
Activity                        <- the audit log; every tool call, filterable
│
Settings                        <- providers, keys, connectors, permissions, Termux, about
```

**The completeness rule:** every state blocked on a human surfaces on the home screen — approvals, drafts, failed routines, `@user` escalations, staged memory writes. If a Bot is waiting on you and home doesn't say so, that is a bug, not a missing feature.

**Objects and ownership:**

| Object | Shared or per-Bot | Why |
|---|---|---|
| Tools, Skills, Connectors | **Account** (shared library) | Many Bots need to browse the web, read documents, or send email. Capabilities are shared. |
| Memory, Routines, Workspace | **Per Bot** | They reflect what that particular role knows and does over time. Context stays with the role. |
| Group chats | Shared context, per-Bot memory | A project conversation, while each Bot keeps its specialization. |

---

## 4. Navigation

Bottom navigation, **three** destinations:

| Tab | Purpose |
|---|---|
| **Bots** | The roster. Home. Needs-you section at the top, then Groups, Pinned, All bots. |
| **Activity** | The audit log and run history, filterable by bot, tool, outcome. |
| **Settings** | Providers, keys, connectors, permissions, Termux, about. |

**Why three, not four:** GrokBot's primary object is the Bot, and its home is the roster. A separate "Today" tab duplicates what the roster already shows through presence and the needs-you section. The needs-you section *is* the triage surface — embedded in the roster, not a separate destination.

**Bot detail** is a pushed screen with a top tab row: **Chat / Routines / Memory / Workspace / Settings**.

**Inspector:** a slide-over panel (bottom sheet on small screens) showing the Bot's live workspace — files created, commands run, Termux output. Opened from the chat's top bar.

**Search:** from the home top bar, across Messages, Bots, Groups, Files, Routines.

---

## 5. Screen inventory

### 5.1 Bots (home)

- **Needs you** section: approval cards, drafts awaiting send, failed routines, `@user` escalations, staged memory writes. Each with a countdown / expires-at.
- **Groups** section: group chat rows (avatar stack, name, members, last message).
- **Pinned bots** row (horizontal scroll of avatars).
- **All bots** list: avatar (with presence state), name, title, last message preview, last active time, unread count (cap 99+).
- **+** button → **New Bot** or **New Group Chat**.
- **Search** icon → global search.
- Empty state: "No bots yet. Create your first teammate."

### 5.2 Bot detail — Chat

- **Top bar:** avatar (presence), name, title, model; actions: search, inspector, settings.
- **Transcript:** user bubbles, assistant bubbles (markdown), inline cards.
- **Inline cards:** tool call (⚙️ name → ✅/❌), approval card, routine card, skill diff, draft.
- **Composer:** pill input, send button, attachment (image/file), voice input.
- **Presence:** avatar animates while the Bot is working; a "Working…" indicator with a stop button.
- **Inspector:** slide-over showing live workspace activity.

### 5.3 Bot detail — Routines

- List of routines: name, schedule, next run, last run, status (active / paused / failed), run history.
- **Add routine** → a draft message in the Bot's chat (GrokBot pattern): "I want a routine that…" — the Bot drafts it, you approve.
- **Run now** button, pause/resume, delete (swipe).
- Run history: last 20 runs with outcome and failure reason.

### 5.4 Bot detail — Memory

- `MEMORY.md` editor (bounded, with a character counter).
- `USER.md` (shared, app-wide).
- Facts list (add / remove).
- Staged writes pending approval.

### 5.5 Bot detail — Workspace

- File browser for the Bot's workspace (`bots/<id>/workspace/`).
- Files created by the Bot, with previews.
- **Open in Termux** action.
- Storage usage.

### 5.6 Bot detail — Settings

- Name, title, avatar (color / emoji), persona (`SOUL.md`).
- Model, temperature, provider.
- Tool grants (checklist with tier toggles).
- Connectors (MCP servers).
- Budget (daily token cap, FGS minutes).
- Danger zone: delete bot, export bot.

### 5.7 Groups

- List of group chats: avatar stack, name, members, last message.
- Group chat: transcript with attribution ("Message from 🤖 Researcher"), @mention routing, rounds indicator, needs-you badge.
- New group: pick 2–6 bots, name, purpose.

### 5.8 Activity

- The full audit log: every tool call — bot, tool, args, result, tier decision, duration, timestamp.
- Filters: by bot, by tool, by outcome.
- Run history: every routine run with outcome and failure reason.
- Export.

### 5.9 Settings

- **Providers:** endpoint, model, key (Keystore), presets (Ollama cloud, OpenAI, OpenRouter, Groq, DeepSeek).
- **Connectors:** MCP servers, Termux packages, intents.
- **Permissions:** notification listener, SMS, contacts, calendar, location, camera, mic, storage, accessibility, Shizuku, battery optimization.
- **Termux:** status, bootstrap, SSH, proot-distro, package install.
- **Appearance:** theme (dark-first), avatar style.
- **About:** version, licenses, update check.

### 5.10 Onboarding

- Welcome → provider setup → permissions walkthrough → battery optimization → first Bot (template chips).
- Honest about what each permission unlocks.

### 5.11 New Bot wizard

1. **What is this Bot for?** — name + one-line job, with template chips (Researcher, Inbox Triage, Morning Brief, Ops Desk).
2. **Who is it?** — persona, drafted from the job, editable.
3. **What does it look like?** — avatar (color / emoji, auto-generated).
4. **What can it touch?** — tool grants as a checklist with tier toggles.
5. **When does it run?** — optional first routine. Can skip.

### 5.12 Approval card (one component, everywhere)

- Operation + inputs + risk warning.
- **Allow once / Always allow / Deny.**
- Countdown for background cards (30 min).
- Rendered inline in chat, on home, and in the notification shade.

---

## 6. Core flows

### 6.1 Create a Bot

**+ → New Bot →** wizard (name, persona, avatar, tools, routine) → **Bot born** → chat opens with a draft first message.

GrokBot's pattern: the first message gives the Bot its job, sources, and approval boundaries. The wizard's final step opens the chat with a pre-filled draft:

> "You are [name]. Your job is [job]. You can use [tools]. Always ask before [boundaries]."

### 6.2 Chat and tool use

1. The user sends a message.
2. The Bot streams a response.
3. If the Bot needs a tool, an inline **tool card** appears: ⚙️ name → running → ✅/❌.
4. If the tool is `ask` tier, an **approval card** appears; the Bot waits.
5. The user approves / denies; the result feeds back; the Bot continues.
6. The final answer streams in.

**No raw tool syntax ever reaches the transcript.** Only the card.

### 6.3 Approval

1. A tool call matches an `ask` rule (or a Custom Rule).
2. An approval card appears inline, on home (if background), and in the notification shade.
3. **Allow once** → runs, no rule saved.
4. **Always allow** → runs, saves an Allow rule.
5. **Deny** → the Bot is told; it doesn't retry until the user sends another message.
6. Background cards expire after 30 minutes; the action is **not** taken, and the Bot is told.

### 6.4 Routine

1. **Add routine** → a draft message in the Bot's chat: "I want a routine that…"
2. The Bot drafts the routine (schedule, prompt, approval boundary, missing-source behavior).
3. A **routine card** appears; the user approves.
4. The routine is scheduled (exact alarm + WorkManager fallback).
5. When it runs, the result lands in the Bot's chat as `⛏ Routine: name` and a notification.
6. The transcript shows what ran; the user can review, edit, pause, or delete.

### 6.5 Skill

1. The Bot proposes a skill (or the user says "save that as a skill").
2. A **skill diff card** appears in the chat.
3. The user approves; the skill is written to the Bot's skills directory.
4. The skill's name + description is in the prompt from then on; the body loads on demand.

### 6.6 Group chat

1. **+ → New Group Chat →** pick 2–6 bots, name, purpose.
2. The user sends a message; @mention routes to the right Bot (or auto-router).
3. Bots reply with attribution; up to 3 rounds, 10 messages per send; bots may PASS.
4. `@user` escalates with a needs-you badge.

### 6.7 Search

1. Search icon on home.
2. Results grouped: Messages, Bots, Groups, Files, Routines.
3. Tapping a result opens the relevant screen.

### 6.8 Presence

| State | Avatar | Title | Where shown |
|---|---|---|---|
| **Idle** | calm | "Online" / last active | roster, chat top bar |
| **Working** | animated pulse | "Working…" + current action | roster, chat, notification |
| **Waiting** | amber ring | "Waiting for you" | roster, chat, home (needs you) |
| **Needs help** | red ring | "Needs help" + reason | roster, chat, home (needs you) |
| **Offline** | grey | "Offline" | roster |

Presence is driven by the agent runtime: Working while a turn is running, Waiting while an approval is pending, Needs help if a turn failed or a routine was skipped. Tapping the avatar opens the inspector.

---

## 7. Inline cards & structured UI

GrokBot's principle: a Bot answers in prose when prose fits, and uses structured UI when it does not. Ogabot's inline cards:

| Card | When | Actions |
|---|---|---|
| **Tool call** | the Bot is using a tool | none (status only) |
| **Approval** | an action needs authorization | Allow once / Always / Deny |
| **Routine** | a routine was created / edited | Approve / Edit / Dismiss |
| **Skill diff** | a skill was proposed | Approve / Reject / View diff |
| **Draft** | a routine composed an outbound message | Send / Edit / Discard |
| **Memory write** | a memory change is staged | Approve / Reject |
| **Run result** | a routine ran | Open / Review / Rerun |
| **Error** | a turn failed | Retry / Dismiss |

One card component, one state model, rendered inline in chat, on home, and in the notification shade.

---

## 8. Bot profile & identity

- **Avatar:** deterministic gradient from the Bot id (stable across reinstalls), or an emoji, or an AI-generated portrait (post-v1).
- **Name:** short, human.
- **Title:** one-line role ("Research assistant", "Inbox triage").
- **Persona (`SOUL.md`):** the system prompt, human-readable and editable.
- **Presence:** state-driven.
- **Color:** six gradient families; the avatar is recognizable at a glance.

GrokBot's avatar design: simple shapes, expressive eyes, controlled variations, motion shows state. On Android we use a deterministic gradient + emoji/initial, with a state ring and animation. Post-v1: original vector avatars with expressive eyes.

---

## 9. Visual language & design system

- **Dark-first.** Near-black surfaces (`#16151A` background, `#1D1C22` surface, `#2F2D32` high surface), subtle 1px separators, light shadows.
- **Accent:** per-bot gradient; a single app accent (`#9BA8FF`) for primary actions.
- **Typography:** Inter variable font; body 15sp, title 16sp, headline 22sp.
- **Bubbles:** rounded (24dp); assistant bubbles surfaceVariant, user bubbles primary @ 18%.
- **Composer:** pill, 26dp radius, max 4 lines, attachment + voice + send.
- **Status colors:** green (ran clean), amber (needs you), red (failed), grey (idle).
- **Motion:** minimal. A pulse for Working, a ring for Waiting, a shake for Needs help. No decorative animation during a turn.
- **Material 3 Expressive.** Standard components, no custom invention.

---

## 10. Android adaptations (GrokBot → Ogabot)

| GrokBot (cloud computer) | Ogabot (the phone is the computer) |
|---|---|
| Bot's cloud computer | The phone + Termux |
| Connectors (Slack, Notion, Drive) | MCP servers, intents, Termux packages, Android capabilities |
| Sidebar (desktop) | Bottom navigation (mobile) |
| Inspector (side panel) | Inspector (slide-over / bottom sheet) |
| Voice call | Voice note + SpeechRecognizer |
| Cloud computer status (purple icon) | Bot presence (avatar state + workspace activity) |
| Takeover (full-screen computer) | Takeover (open Termux / workspace / accessibility session) |
| Files on the cloud computer | Files in the Bot's workspace on the phone |
| Routines run in the cloud | Routines run on the phone (WorkManager + AlarmManager) |
| Approvals from routines | Approvals from routines (30-min expiry) |

**The phone's capabilities are the differentiator:** notifications (listener), SMS, contacts, calendar, location, camera, mic, sensors, accessibility, Shizuku, Termux. A cloud Bot can browse your bank's website. It cannot read the SMS your bank just sent, run a Python script over it, and file the result in your budget spreadsheet. Ogabot can.

---

## 11. What we deliberately don't build

- **A marketing dashboard.** No metrics, no charts, no "productivity score."
- **A custom navigation invention.** Standard bottom nav + Material 3.
- **Raw tool syntax in the transcript.** Never.
- **Autonomous bot creation.** A Bot drafts; a human approves.
- **A cloud computer.** The phone is the computer.
- **On-device model inference by default.** BYOK; local LLM provisioned but not default.

---

## 12. Design tokens (initial)

```
background        #16151A
surface           #1D1C22
surfaceHigh       #2F2D32
onSurface         #E6E1E5
onSurfaceVariant  #9A959F
accent            #9BA8FF
success           #9FE0C8
warning           #FFC940
danger            #FF7A7A
code              #111014 / #9FE0C8

avatar gradients  6 families (indigo→violet, teal→blue, orange→red,
                  yellow→orange, green→teal, violet→pink)
radius            24dp bubbles, 26dp composer, 16-20dp cards
font              Inter variable (400/500/600/700)
```

---

## 13. Open design questions

1. **Avatar system:** deterministic gradient + initial for v1, or invest in original vector avatars with expressive eyes from the start?
2. **Inspector:** bottom sheet (standard) or a dedicated Workspace tab (already in the bot detail)?
3. **Groups:** a section in the home roster, or a dedicated tab?
4. **Routine creation:** draft-message-in-chat (GrokBot) or a structured form? GrokBot's is more natural; the form is more predictable.
5. **Search:** global search from home, or per-bot search in the chat?
