/* FireUp Android: prístup len pre povolený účet + kontrola aktualizácií */
(function () {
  "use strict";
  var ALLOWED = ["d49eee7c5c5a31f951dd0670f8d1427229ef2527063f47762bc404d5299a38bf", "03939395fcb899d193dacc6e2e37ef57e495052c41b6865350b04502e7280067"];
  var REPO = "Olmo4522/FRIEUP";
  var KEY = "fireup_auth";
  var params = new URLSearchParams(location.search);
  var CUR = parseInt(params.get("v") || "0", 10);

  function sha(s) {
    return crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)).then(function (b) {
      return Array.from(new Uint8Array(b)).map(function (x) { return x.toString(16).padStart(2, "0"); }).join("");
    });
  }
  function getAuth() { try { return JSON.parse(localStorage.getItem(KEY) || "null"); } catch (e) { return null; } }
  function setAuth(a) { try { localStorage.setItem(KEY, JSON.stringify(a)); } catch (e) {} }
  function tap() { try { window.SFX && SFX.tap(); } catch (e) {} }

  var css = document.createElement("style");
  css.textContent =
    "#gate{position:fixed;inset:0;z-index:200;display:flex;align-items:center;justify-content:center;background:var(--bg,#0b0d0b);" +
    "padding:calc(24px + env(safe-area-inset-top,0px)) max(20px,env(safe-area-inset-right,0px)) calc(24px + env(safe-area-inset-bottom,0px)) max(20px,env(safe-area-inset-left,0px));" +
    "background-image:radial-gradient(70% 45% at 50% 0%,rgba(62,224,90,.22),transparent 70%),radial-gradient(rgba(255,255,255,.045) 1px,transparent 1.2px);background-size:auto,22px 22px}" +
    "#gate .gbox{width:100%;max-width:360px;display:flex;flex-direction:column;gap:14px;animation:rise .5s cubic-bezier(.2,.9,.3,1)}" +
    "#gate .glogo{display:flex;flex-direction:column;align-items:center;gap:8px;margin-bottom:6px}" +
    "#gate .glogo svg{width:64px;height:64px;filter:drop-shadow(0 0 14px rgba(62,224,90,.5))}" +
    "#gate .glogo b{font-family:var(--f-display,system-ui);font-size:26px;letter-spacing:.04em;background:linear-gradient(180deg,#fff,#b9ffc6);-webkit-background-clip:text;background-clip:text;color:transparent}" +
    "#gate .glogo small{color:var(--muted,#8b968a);font-size:13px;text-align:center}" +
    "#gate h2{margin:0;font-family:var(--f-display,system-ui);font-size:18px;text-align:center}" +
    "#gate .gerr{color:#ff4d4d;font-size:13px;text-align:center;min-height:18px;margin:0}" +
    "#gate .pin{letter-spacing:.6em;text-align:center;font-size:22px;font-family:var(--f-mono,monospace)}" +
    "#gate.shake .gbox{animation:gshake .4s}@keyframes gshake{25%{transform:translateX(-8px)}50%{transform:translateX(7px)}75%{transform:translateX(-4px)}}" +
    "#gate.out{animation:fadeOut .35s ease forwards}" +
    ".updbox{width:min(360px,calc(100% - 32px));display:flex;flex-direction:column;gap:10px;padding:22px 20px 18px;border-radius:24px;background:rgba(16,20,16,.95);border:1px solid rgba(62,224,90,.45);box-shadow:0 0 50px -10px rgba(62,224,90,.5);animation:pop .45s cubic-bezier(.2,1.4,.4,1);text-align:center}" +
    ".updbox .ui{width:56px;height:56px;margin:0 auto;border-radius:18px;display:grid;place-items:center;background:linear-gradient(150deg,#b6ff7a,#3ee05a 50%,#17a63a);color:#062a10;box-shadow:0 6px 22px -6px rgba(62,224,90,.6)}" +
    ".updbox .ui svg{width:28px;height:28px}" +
    ".updbox small{font-family:var(--f-mono,monospace);letter-spacing:.2em;color:#b9ffc6;font-size:11px}" +
    ".updbox b{font-family:var(--f-display,system-ui);font-size:22px}" +
    ".updbox p{margin:0;color:var(--muted,#8b968a);font-size:14px;line-height:1.5}" +
    ".updbox .ubar{height:6px;border-radius:6px;background:rgba(255,255,255,.08);overflow:hidden}.updbox .ubar span{display:block;height:100%;width:30%;background:linear-gradient(90deg,#1fae3c,#b6ff7a);animation:ubar 1.1s ease-in-out infinite}" +
    "@keyframes ubar{0%{transform:translateX(-100%)}100%{transform:translateX(340%)}}";
  document.head.appendChild(css);

  var FLAME = '<svg viewBox="0 0 24 24"><defs><linearGradient id="gfl" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#1fae3c"/><stop offset="1" stop-color="#b6ff7a"/></linearGradient></defs><path fill="url(#gfl)" d="M12 2c.6 3.2 4.8 5.4 5.6 9.6.9 4.8-2.4 10.4-7.4 10.4-3.9 0-6.7-3-6.7-6.6 0-3 1.8-4.8 3.3-6.4.3 1.6 1 2.7 2.1 3.3C8.5 8.6 10.6 5.3 12 2Z"/><path fill="#0b0d0b" opacity=".55" d="M11.6 13c1.4 1.5 3 2.6 3 4.6a3.1 3.1 0 0 1-6.2.1c0-1.6 1-2.4 1.8-3.2.1.8.4 1.3 1 1.6-.3-1.1.1-2.2.4-3.1Z"/></svg>';

  var gate = document.createElement("div");
  gate.id = "gate";
  document.body.appendChild(gate);
  var fails = 0, lockUntil = 0;

  function shake(msg) {
    var e = gate.querySelector(".gerr"); if (e) e.textContent = msg;
    gate.classList.remove("shake"); void gate.offsetWidth; gate.classList.add("shake");
    try { window.SFX && SFX.undo(); navigator.vibrate && navigator.vibrate([40, 40, 40]); } catch (x) {}
  }
  function open() {
    try { window.SFX && SFX.done(); } catch (e) {}
    gate.classList.add("out");
    setTimeout(function () { gate.remove(); checkUpdate(); }, 350);
  }

  function showRegister() {
    gate.innerHTML = '<form class="gbox" id="gReg" novalidate><div class="glogo">' + FLAME + '<b>FIREUP</b><small>Buduj disciplínu. Nie motiváciu.</small></div>' +
      '<h2>Registrácia</h2>' +
      '<div class="field"><label for="gEmail">Email</label><input class="inp" id="gEmail" type="email" autocomplete="email" inputmode="email" placeholder="tvoj@email.com"></div>' +
      '<div class="field"><label for="gPin">Nový PIN (4 až 6 číslic)</label><input class="inp pin" id="gPin" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password"></div>' +
      '<div class="field"><label for="gPin2">Zopakuj PIN</label><input class="inp pin" id="gPin2" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password"></div>' +
      '<p class="gerr"></p><button class="btn block" type="submit">Zaregistrovať sa</button></form>';
    gate.querySelector("#gReg").onsubmit = function (ev) {
      ev.preventDefault(); tap();
      var email = gate.querySelector("#gEmail").value.trim().toLowerCase();
      var p1 = gate.querySelector("#gPin").value, p2 = gate.querySelector("#gPin2").value;
      if (!email) return shake("Zadaj email.");
      sha(email).then(function (h) {
        if (ALLOWED.indexOf(h) < 0) return shake("Tento email nemá prístup do FireUp.");
        if (!/^\d{4,6}$/.test(p1)) return shake("PIN musí mať 4 až 6 číslic.");
        if (p1 !== p2) return shake("PIN-y sa nezhodujú.");
        var salt = Math.random().toString(36).slice(2);
        sha(salt + p1).then(function (ph) { setAuth({ e: h, s: salt, p: ph, len: p1.length }); open(); });
      });
    };
    setTimeout(function () { var i = gate.querySelector("#gEmail"); i && i.focus(); }, 400);
  }

  function showLogin(a) {
    gate.innerHTML = '<form class="gbox" id="gLog" novalidate><div class="glogo">' + FLAME + '<b>FIREUP</b><small>Vitaj späť</small></div>' +
      '<div class="field"><label for="gPinL">Zadaj PIN</label><input class="inp pin" id="gPinL" type="password" inputmode="numeric" maxlength="6" autocomplete="current-password"></div>' +
      '<p class="gerr"></p><button class="btn block" type="submit">Odomknúť</button>' +
      '<button class="btn ghost block" type="button" id="gForgot">Zabudol som PIN</button></form>';
    var inp = gate.querySelector("#gPinL");
    function tryPin() {
      if (Date.now() < lockUntil) return shake("Príliš veľa pokusov. Skús o " + Math.ceil((lockUntil - Date.now()) / 1000) + " s.");
      sha(a.s + inp.value).then(function (ph) {
        if (ph === a.p) return open();
        fails++; inp.value = "";
        if (fails >= 5) { lockUntil = Date.now() + 30000; fails = 0; return shake("5 nesprávnych pokusov. Počkaj 30 sekúnd."); }
        shake("Nesprávny PIN.");
      });
    }
    gate.querySelector("#gLog").onsubmit = function (ev) { ev.preventDefault(); tap(); tryPin(); };
    inp.addEventListener("input", function () { if (inp.value.length === a.len) tryPin(); });
    gate.querySelector("#gForgot").onclick = function () { tap(); showReset(); };
    setTimeout(function () { inp.focus(); }, 400);
  }

  function showReset() {
    gate.innerHTML = '<form class="gbox" id="gRes" novalidate><div class="glogo">' + FLAME + '<b>FIREUP</b><small>Nový PIN</small></div>' +
      '<div class="field"><label for="gEmailR">Tvoj email</label><input class="inp" id="gEmailR" type="email" inputmode="email" autocomplete="email"></div>' +
      '<div class="field"><label for="gPinR">Nový PIN (4 až 6 číslic)</label><input class="inp pin" id="gPinR" type="password" inputmode="numeric" maxlength="6"></div>' +
      '<p class="gerr"></p><button class="btn block" type="submit">Nastaviť PIN</button><button class="btn ghost block" type="button" id="gBack">Späť</button></form>';
    gate.querySelector("#gBack").onclick = function () { tap(); showLogin(getAuth()); };
    gate.querySelector("#gRes").onsubmit = function (ev) {
      ev.preventDefault(); tap();
      var email = gate.querySelector("#gEmailR").value.trim().toLowerCase(), p = gate.querySelector("#gPinR").value;
      sha(email).then(function (h) {
        if (ALLOWED.indexOf(h) < 0) return shake("Tento email nemá prístup do FireUp.");
        if (!/^\d{4,6}$/.test(p)) return shake("PIN musí mať 4 až 6 číslic.");
        var salt = Math.random().toString(36).slice(2);
        sha(salt + p).then(function (ph) { setAuth({ e: h, s: salt, p: ph, len: p.length }); open(); });
      });
    };
  }

  /* ---------- aktualizácie ---------- */
  function checkUpdate() {
    if (!CUR) return;
    fetch("https://api.github.com/repos/" + REPO + "/releases/latest", { headers: { Accept: "application/vnd.github+json" } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (rel) {
        if (!rel || !rel.tag_name) return;
        var n = parseInt(String(rel.tag_name).split(".").pop(), 10);
        if (!(n > CUR)) return;
        var asset = (rel.assets || []).filter(function (x) { return /\.apk$/i.test(x.name); })[0];
        if (!asset) return;
        showUpdate(rel.tag_name, asset.browser_download_url);
      }).catch(function () {});
  }
  function showUpdate(tag, url) {
    var layer = document.getElementById("layer");
    layer.innerHTML = '<div class="scrim lvup" id="scrim"><div class="updbox" role="dialog" aria-modal="true">' +
      '<div class="ui"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v11M7 10l5 5 5-5M5 20h14"/></svg></div>' +
      '<small>NOVÁ AKTUALIZÁCIA</small><b>FireUp ' + tag + '</b><p>Je dostupná nová verzia. Tvoje úlohy, XP aj levely ostanú zachované.</p>' +
      '<div class="ubar" hidden><span></span></div><p class="ust" hidden></p>' +
      '<button class="btn block" id="uGo">Aktualizovať</button><button class="btn ghost block" id="uLater">Neskôr</button></div></div>';
    try { window.SFX && SFX.quest(); } catch (e) {}
    var go = document.getElementById("uGo");
    document.getElementById("uLater").onclick = function () { tap(); if (window.closeLayer) closeLayer(); else layer.innerHTML = ""; };
    go.onclick = function () {
      tap();
      if (window.FireUpNative && FireUpNative.update) {
        go.disabled = true; go.textContent = "Sťahujem…";
        layer.querySelector(".ubar").hidden = false;
        FireUpNative.update(url);
      } else { location.href = url; }
    };
  }
  window.__updateStatus = function (state, msg) {
    var st = document.querySelector(".updbox .ust"), go = document.getElementById("uGo"), bar = document.querySelector(".updbox .ubar");
    if (!st) return;
    st.hidden = false; st.textContent = msg || "";
    if (state !== "downloading") { if (bar) bar.hidden = true; if (go) { go.disabled = false; go.textContent = state === "done" ? "Inštalovať znova" : "Skúsiť znova"; } }
  };

  var a = getAuth();
  if (a && ALLOWED.indexOf(a.e) >= 0 && a.p) { if (!a.len) a.len = 0; showLogin(a); } else showRegister();
})();
