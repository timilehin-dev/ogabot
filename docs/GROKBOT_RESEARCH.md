# Grok Bot (xAI) — Research Notes for Ogabot
Sources: x.ai announcement, docs.x.ai/grok-bot (overview / skills-routines / approvals-security), independent breakdowns. 2026-11-07.

## Copy into Ogabot
1. **Teach-a-task**: demonstrate once → draft skill → review → test → schedule. (Ours: record conversation flow; later accessibility teach mode.)
2. **Skill anatomy**: when to use / inputs+access / sequence / validation / output / WHAT REQUIRES APPROVAL. Shared library across bots, per-bot enablement, publish/export.
3. **Routine ops**: 50/bot cap, 20 run records kept, >=5min spacing, rate limits, run history w/ failure reasons, Test button, pause/resume, timezone per routine, delete-cascades.
4. **Triggers**: webhook (POST+Bearer, 200=queued), Slack-like events → our phone equivalents: notification-listener, incoming email/SMS, charge/unlock, app events.
5. **Auto Review approvals**: model-checked every action; card = operation+inputs; Allow once / Always allow (saves Custom Rule) / Deny; risk warnings on cards; background cards EXPIRE after 30 min (action not taken); Custom Rules in plain language (Ask-first wins; 20+20); in group chats only the requester approves; text from email/webpages NEVER counts as user authorization (prompt-injection defense).
6. **Boundaries**: recommend-approval list (send/publish/purchase/delete/permissions/production/legal); routine outbound email waits as DRAFT until human sends; Secure Forms mask secrets from the model; human takeover for 2FA/CAPTCHA.
7. **Prebuilt bot marketplace**: Office Ops Desk, Event Producer, Morning Newspaper, Clip Bot → Ogabot starter templates.
8. **Team pattern**: Coordinator + Researcher + Builder + Reviewer (cannot approve own work) + Human. Offer as wizard preset.
9. **Voice**: calls + voice notes trigger work like text.

## GrokBot weaknesses Ogabot exploits
- No self-hosting → we run 100% on-device
- No memory inspection/export → our memory is visible, editable, exportable
- Shared-computer trust blur → per-bot workspaces + audit log
- No model picker → any OpenAI-compatible provider, per bot
- Usage-quota anxiety → user's own keys + cost dashboard
- NO ANDROID APP → we are Android-native (Nigeria/Africa-first)

## Full research data
See grokbot_research.json in project workspace.
