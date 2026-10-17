/*
 * The GitHub Pages copy of the admin dashboard.
 *
 * It is only the interface. The numbers, the password check and the About
 * text all live in the Cloudflare Worker, at the address in config.js:
 *
 *   POST {api}/v1/login   {email, password}  →  {token, expiresAt}
 *   GET  {api}/v1/stats?days=N               →  counts, with the token
 *   GET  {api}/v1/about                      →  the About text (public)
 *   PUT  {api}/v1/about                      →  saves it, with the token
 *
 * The Worker hands back a signed session, which travels in an Authorization
 * header: this page is on github.io and the Worker on workers.dev, and a cookie
 * across two sites is a third-party cookie that browsers drop. It is kept in
 * sessionStorage — not in the address, not in the page — and disappears when
 * the tab closes.
 */
(function () {
  "use strict";

  var config = window.ADMIN_CONFIG || {};
  var API = String(config.apiBase || "").replace(/\/+$/, "");
  var STORE_KEY = "mc_admin_session";
  var session = null;

  function el(id) {
    return document.getElementById(id);
  }

  /* ------------------------------------------------------------------ *
   * Storage that cannot break the page
   *
   * Reading browser storage throws when site data is blocked (some privacy
   * settings, in-app browsers, sandboxed frames). A throw on the first line
   * used to leave a whole dashboard dead, so every call is guarded and a
   * blocked browser simply means "not remembered".
   * ------------------------------------------------------------------ */
  var store = (function () {
    try {
      var probe = "mc_probe";
      window.sessionStorage.setItem(probe, "1");
      window.sessionStorage.removeItem(probe);
      return window.sessionStorage;
    } catch (error) {
      var memory = {};
      return {
        getItem: function (key) {
          return Object.prototype.hasOwnProperty.call(memory, key) ? memory[key] : null;
        },
        setItem: function (key, value) {
          memory[key] = String(value);
        },
        removeItem: function (key) {
          delete memory[key];
        },
      };
    }
  })();

  function readSession() {
    try {
      var raw = store.getItem(STORE_KEY);
      if (!raw) return null;
      var saved = JSON.parse(raw);
      if (!saved || !saved.token) return null;
      if (Number(saved.expiresAt || 0) < Date.now() + 60 * 1000) return null;
      return saved;
    } catch (error) {
      return null;
    }
  }

  function writeSession(value) {
    try {
      if (value) store.setItem(STORE_KEY, JSON.stringify(value));
      else store.removeItem(STORE_KEY);
    } catch (error) {
      /* fine: it just will not be remembered */
    }
  }

  /* ------------------------------------------------------------------ *
   * Talking to the Worker
   * ------------------------------------------------------------------ */
  function request(path, options) {
    options = options || {};
    var headers = {};
    if (options.json !== undefined) headers["content-type"] = "application/json";
    if (session && session.token) headers.authorization = "Bearer " + session.token;

    return fetch(API + path, {
      method: options.method || "GET",
      headers: headers,
      body: options.json === undefined ? undefined : JSON.stringify(options.json),
    }).then(function (response) {
      if (response.status === 401 && options.guarded) {
        signOut("Your session ended. Sign in again.");
        throw new Error("signed out");
      }
      return response.json().then(
        function (data) {
          return { ok: response.ok, status: response.status, data: data };
        },
        function () {
          return { ok: response.ok, status: response.status, data: {} };
        }
      );
    });
  }

  /* ------------------------------------------------------------------ *
   * Showing one screen or the other
   * ------------------------------------------------------------------ */
  function showSignIn(message) {
    el("signin-panel").hidden = false;
    el("app").hidden = true;
    el("signout-form").hidden = true;
    el("signin-status").textContent = message || "";
    el("who").textContent = "Sign in to see the numbers and edit the About page.";
    el("password").value = "";
    el("email").focus();
  }

  function showDashboard() {
    el("signin-panel").hidden = true;
    el("app").hidden = false;
    el("signout-form").hidden = false;
    el("who").textContent = "Signed in as " + session.email + ".";
    loadStats();
    loadAbout();
  }

  function signOut(message) {
    session = null;
    writeSession(null);
    showSignIn(message || "");
  }

  /* ------------------------------------------------------------------ *
   * Sign in
   * ------------------------------------------------------------------ */
  el("signin-form").addEventListener("submit", function (event) {
    event.preventDefault();
    var email = el("email").value.trim();
    var password = el("password").value;
    var button = el("signin-button");

    if (!API) {
      el("signin-status").textContent =
        "config.js has no apiBase — put the Worker's address in it.";
      return;
    }

    button.disabled = true;
    el("signin-status").textContent = "Signing in…";

    fetch(API + "/v1/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: email, password: password }),
    })
      .then(function (response) {
        return response.json().then(function (data) {
          if (!response.ok) {
            throw new Error(data.error || "The Worker answered " + response.status + ".");
          }
          return data;
        });
      })
      .then(function (data) {
        session = {
          token: data.token,
          email: data.email || email,
          expiresAt: data.expiresAt || Date.now() + 30 * 86400000,
        };
        writeSession(session);
        el("signin-status").textContent = "";
        showDashboard();
      })
      .catch(function (error) {
        el("signin-status").textContent =
          error.message === "Failed to fetch"
            ? "The Worker could not be reached. Check the connection, and the address in config.js."
            : error.message;
      })
      .then(function () {
        button.disabled = false;
      });
  });

  el("signout-form").addEventListener("submit", function (event) {
    event.preventDefault();
    signOut("");
  });

  /* ------------------------------------------------------------------ *
   * The numbers
   * ------------------------------------------------------------------ */
  function number(value) {
    return Number(value || 0).toLocaleString();
  }

  function addCell(row, text) {
    var cell = document.createElement("td");
    cell.textContent = text;
    row.appendChild(cell);
    return cell;
  }

  function renderStats(data) {
    var totals = data.totals || {};
    var active = data.active || {};

    el("stamp").textContent = "as of " + new Date(data.generatedAt).toLocaleString();
    el("load-status").textContent = "";

    var cards = [
      ["Calculations", totals.calc],
      ["AI questions", totals.ai],
      ["App opens", totals.app_open],
      ["Installs (all time)", active.installs],
      ["Active today", active.dau],
      ["Active this week", active.wau],
      ["Active in range", active.mau],
    ];
    el("cards").innerHTML = cards
      .map(function (card) {
        return (
          '<div class="card"><div class="k">' +
          number(card[1]) +
          '</div><div class="t">' +
          card[0] +
          "</div></div>"
        );
      })
      .join("");

    // One row per day, with the three counters side by side.
    var perDay = {};
    (data.series || []).forEach(function (row) {
      var day = String(row.day || "");
      perDay[day] = perDay[day] || { app_open: 0, calc: 0, ai: 0, installs: 0 };
      perDay[day][row.kind] = Number(row.total || 0);
    });
    (data.installs || []).forEach(function (row) {
      var day = String(row.day || "");
      perDay[day] = perDay[day] || { app_open: 0, calc: 0, ai: 0, installs: 0 };
      perDay[day].installs = Number(row.total || 0);
    });

    var days = Object.keys(perDay).sort();
    var peak = 1;
    days.forEach(function (day) {
      peak = Math.max(peak, perDay[day].calc || 0);
    });

    var chart = el("chart");
    chart.innerHTML = "";
    days.forEach(function (day) {
      var bar = document.createElement("div");
      var value = perDay[day].calc || 0;
      bar.style.height = Math.max(2, Math.round((value / peak) * 110)) + "px";
      bar.title = day + ": " + number(value) + " calculations";
      chart.appendChild(bar);
    });
    el("chart-empty").hidden = days.length > 0;

    var table = el("table");
    table.innerHTML = "";
    days
      .slice()
      .reverse()
      .forEach(function (day) {
        var row = document.createElement("tr");
        addCell(row, day);
        addCell(row, number(perDay[day].app_open));
        addCell(row, number(perDay[day].calc));
        addCell(row, number(perDay[day].ai));
        table.appendChild(row);
      });

    var versions = el("versions");
    versions.innerHTML = "";
    (data.versions || []).forEach(function (row) {
      var tr = document.createElement("tr");
      addCell(tr, String(row.version || "unknown"));
      addCell(tr, number(row.total));
      versions.appendChild(tr);
    });
    el("versions-empty").hidden = (data.versions || []).length > 0;
  }

  function loadStats() {
    el("load-status").textContent = "Loading…";
    request("/v1/stats?days=" + encodeURIComponent(el("range").value), { guarded: true })
      .then(function (reply) {
        if (!reply.ok) throw new Error(reply.data.error || "The Worker answered " + reply.status + ".");
        renderStats(reply.data);
      })
      .catch(function (error) {
        if (error.message === "signed out") return;
        el("load-status").textContent = "Could not load: " + error.message;
      });
  }

  el("range").addEventListener("change", loadStats);
  el("refresh").addEventListener("click", loadStats);

  /* ------------------------------------------------------------------ *
   * The About page
   * ------------------------------------------------------------------ */
  function loadAbout() {
    el("ab-status").textContent = "Loading…";
    request("/v1/about")
      .then(function (reply) {
        if (!reply.ok) throw new Error(reply.data.error || "The Worker answered " + reply.status + ".");
        var data = reply.data;
        var donate = data.donate || {};
        el("ab-title").value = data.title || "";
        el("ab-tagline").value = data.tagline || "";
        el("ab-body").value = (data.body || []).join("\n");
        el("ab-links").value = (data.links || [])
          .map(function (link) {
            return link.label + " | " + link.url;
          })
          .join("\n");
        el("ab-contact").value = data.contact || "";
        el("ab-donate-on").checked = donate.enabled !== false;
        el("ab-donate-upi").value = donate.upi || "";
        el("ab-donate-name").value = donate.name || "";
        el("ab-status").textContent =
          data.source === "saved"
            ? "Saved copy, last changed " + new Date(data.updated || 0).toLocaleString()
            : "Showing the built-in text — nothing saved yet.";
      })
      .catch(function (error) {
        el("ab-status").textContent = "Could not load: " + error.message;
      });
  }

  el("about-form").addEventListener("submit", function (event) {
    event.preventDefault();
    var links = el("ab-links")
      .value.split("\n")
      .map(function (line) {
        var at = line.indexOf("|");
        return at === -1
          ? { label: line.trim(), url: "" }
          : { label: line.slice(0, at).trim(), url: line.slice(at + 1).trim() };
      })
      .filter(function (link) {
        return link.label || link.url;
      });

    var payload = {
      title: el("ab-title").value,
      tagline: el("ab-tagline").value,
      body: el("ab-body").value.split("\n"),
      links: links,
      contact: el("ab-contact").value,
      donate: {
        enabled: el("ab-donate-on").checked,
        upi: el("ab-donate-upi").value,
        name: el("ab-donate-name").value,
      },
    };

    el("ab-save").disabled = true;
    el("ab-save-status").textContent = "Saving…";
    request("/v1/about", { method: "PUT", json: payload, guarded: true })
      .then(function (reply) {
        if (!reply.ok) throw new Error(reply.data.error || "The Worker answered " + reply.status + ".");
        el("ab-save-status").textContent = "Saved. The app shows it after Refresh on its About page.";
        el("ab-status").textContent =
          "Saved copy, last changed " + new Date(reply.data.updated || 0).toLocaleString();
      })
      .catch(function (error) {
        if (error.message === "signed out") return;
        el("ab-save-status").textContent = "Could not save: " + error.message;
      })
      .then(function () {
        el("ab-save").disabled = false;
      });
  });

  /* ------------------------------------------------------------------ *
   * Start
   * ------------------------------------------------------------------ */
  if (API) {
    el("link-health").href = API + "/health";
    el("link-privacy").href = API + "/privacy";
    el("api-base").textContent = API;
  } else {
    el("api-base").textContent = "nowhere yet — config.js is empty";
  }

  session = readSession();
  if (session) showDashboard();
  else showSignIn("");
})();
