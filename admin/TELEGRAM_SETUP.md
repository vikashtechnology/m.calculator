# Telegram-only admin

## Deploy

1. Open Cloudflare → Workers & Pages → **ai-calculator-stats** → Edit code.
2. Replace the code with `admin/worker/worker.js`, then **Deploy**.
3. Keep the D1 binding named `DB`. Existing data is preserved.
4. Keep your existing secrets `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`.
   The chat ID must be your **positive numeric private-chat/user ID**, not a
   group/channel ID. The bot accepts only private messages from that same user.
   No GitHub environment variable, login password, or third secret is required.

## Register the webhook once (after deploying)

Run locally from a checkout with Python 3 installed:

```sh
python3 tools/setup_telegram.py
```

Enter `https://ai-calculator-stats.rajkatrina90.workers.dev` when prompted.
Paste your bot token at the hidden prompt. Do not paste it into chat, GitHub,
source files, screenshots, or shell command arguments. Telegram allows one
webhook per bot, so this replaces any previous bot webhook.

The script registers `/telegram/webhook` and a secret header derived from the
bot token. Without registering it this way, webhook requests are rejected.
Rotate/re-register the webhook whenever the bot token changes.

Open the bot in Telegram, tap Start, and send `/help`.

## Commands

- `/stats` or `/stats 7` — usage for 1–365 days
- `/about` — current About settings
- `/settitle My Calculator`
- `/settagline Fast maths`
- `/setabout First paragraph` — blank lines separate paragraphs
- `/setcontact help@example.com`
- `/donation` — current donation settings
- `/donation on` or `/donation off`
- `/setupi handle@bank`
- `/setdonorname Display name`

The imported m-calculator-final Android snapshot bundles About and donation
content locally. Bot edits update the Worker database but do not change that
app version. Older app versions that fetch /v1/about can receive the edits.
Existing validation
limits still apply (for example 80-character titles and 12 body paragraphs).
Commands set values, rather than incrementing/toggling, so Telegram retries do
not reverse changes. Delivery retries can send duplicate replies; updates are
not a strictly ordered transaction queue. Avoid sending simultaneous edits.

## Retired web admin

Web login, web stats and About writes are blocked, even if an old
`ADMIN_PUBLIC_ENABLED=true` setting remains. Public About reads, event intake,
privacy and optional AI solve remain available for the Android app.
The Pages workflow is disabled and its source now contains only a retirement
notice. To remove an already published site, use GitHub Settings → Pages →
Unpublish site. Old cached web pages cannot edit anything through the Worker.

`/health` reports `admin.configured`, but this checks configuration presence,
not whether Telegram accepted the token or the webhook was registered. Send
`/help` to verify the complete connection.

## Local tests

```sh
bash tools/run_worker_tests.sh
```

Live Telegram delivery must be tested after deployment; the tests use a stub.
