/*
 * Where the numbers come from.
 *
 * This page is only the interface: the data, the password check and the About
 * text all live in the Cloudflare Worker. Put its address here (no trailing
 * slash). It is the same address the app uses for TELEMETRY_URL.
 */
window.ADMIN_CONFIG = {
  apiBase: "https://ai-calculator-stats.rajkatrina90.workers.dev",
};
