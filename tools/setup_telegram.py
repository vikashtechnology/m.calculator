#!/usr/bin/env python3
"""Run locally to register the Telegram webhook. Never prints the bot token."""
import getpass
import hashlib
import json
import urllib.request
from urllib.parse import urlparse

base = input('Worker HTTPS URL: ').strip().rstrip('/')
u = urlparse(base)
if u.scheme != 'https' or not u.hostname or u.path or u.query or u.fragment:
    raise SystemExit('Enter an HTTPS origin only, without a path or query.')
token = getpass.getpass('Telegram bot token (hidden): ').strip()
if not token:
    raise SystemExit('Token is required.')
secret = hashlib.sha256(('telegram-webhook:' + token).encode()).hexdigest()
payload = json.dumps({'url': base + '/telegram/webhook', 'secret_token': secret,
                      'allowed_updates': ['message']}).encode()
req = urllib.request.Request('https://api.telegram.org/bot' + token + '/setWebhook',
                             data=payload, headers={'Content-Type': 'application/json'})
try:
    with urllib.request.urlopen(req, timeout=30) as response:
        result = json.load(response)
except Exception:
    raise SystemExit('Registration failed. Check the token and connection; no credentials were printed.')
print('Webhook registered. Send /help to your bot.' if result.get('ok') else
      'Telegram rejected registration. Check your bot token and Worker URL.')
