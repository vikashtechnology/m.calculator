#!/usr/bin/env node
/**
 * Prints an ADMIN_PASSWORD_HASH for the dashboard's sign-in.
 *
 *     node admin/worker/hash-password.mjs 'my new password'
 *
 * Then store it in Cloudflare as a secret (Workers & Pages → the Worker →
 * Settings → Variables and Secrets → Add → ADMIN_PASSWORD_HASH, type Secret),
 * and set ADMIN_EMAIL to the address that should sign in. Redeploy is not
 * needed: secrets take effect on the next request.
 *
 * The password itself is never stored anywhere — only this hash, which cannot
 * be turned back into the password.
 */
import { pbkdf2Sync, randomBytes } from "node:crypto";

const iterations = 120_000;
const password = process.argv[2];

if (!password) {
  console.error("usage: node admin/worker/hash-password.mjs 'the new password'");
  process.exit(2);
}
if (password.length < 8) {
  console.error("Use at least 8 characters — the dashboard is on the public internet.");
  process.exit(2);
}

const salt = randomBytes(16);
const hash = pbkdf2Sync(password, salt, iterations, 32, "sha256");
const encode = (buffer) => buffer.toString("base64url");

console.log(`pbkdf2$${iterations}$${encode(salt)}$${encode(hash)}`);
