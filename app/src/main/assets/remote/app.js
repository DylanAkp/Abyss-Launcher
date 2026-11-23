const TOKEN_KEY = "abyss_remote_token";
let token = localStorage.getItem(TOKEN_KEY) || "";
let assetTicket = "";
let assetTicketExpiresAt = 0;
let eventSource = null;
let lastState = null;

const pairScreen = document.getElementById("pair-screen");
const remoteScreen = document.getElementById("remote-screen");
const pinInput = document.getElementById("pin-input");
const pairBtn = document.getElementById("pair-btn");
const pairError = document.getElementById("pair-error");
const connDot = document.getElementById("conn-dot");
const connLabel = document.getElementById("conn-label");
const ringBtn = document.getElementById("ring-remote-btn");

const PLAY_PATH = "M8 5v14l11-7z";
const PAUSE_PATH = "M6 19h4V5H6v14zm8-14v14h4V5h-4z";

function haptic() {
  if (navigator.vibrate) navigator.vibrate(15);
}

async function api(path, body) {
  const res = await fetch(path, {
    method: body !== undefined ? "POST" : "GET",
    headers: {
      "Content-Type": "application/json",
      "X-Remote-Token": token
    },
    body: body !== undefined ? JSON.stringify(body) : undefined
  });
  if (res.status === 401) {
    clearToken();
    throw new Error("Unauthorized");
  }
  return res;
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  token = "";
  assetTicket = "";
  assetTicketExpiresAt = 0;
  showPair();
}

async function getAssetTicket() {
  if (assetTicket && Date.now() < assetTicketExpiresAt) return assetTicket;
  const res = await api("/api/ticket");
  const data = await res.json();
  assetTicket = data.ticket;
  assetTicketExpiresAt = Date.now() + 45000;
  return assetTicket;
}

async function pair(pin) {
  pairError.textContent = "";
  try {
    const res = await fetch("/api/pair", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pin, deviceName: navigator.platform || "Phone" })
    });
    if (!res.ok) {
      pairError.textContent = "Invalid PIN";
      return false;
    }
    const data = await res.json();
    token = data.token;
    localStorage.setItem(TOKEN_KEY, token);
    assetTicket = "";
    assetTicketExpiresAt = 0;
    showRemote();
    return true;
  } catch {
    pairError.textContent = "Connection failed";
    return false;
  }
}

function showPair() {
  if (eventSource) { eventSource.close(); eventSource = null; }
  assetTicket = "";
  assetTicketExpiresAt = 0;
  pairScreen.classList.remove("hidden");
  remoteScreen.classList.add("hidden");
}

function showRemote() {
  pairScreen.classList.add("hidden");
  remoteScreen.classList.remove("hidden");
  connectSse();
  api("/api/state").then(r => r.json()).then(renderState).catch(() => {});
}

async function connectSse() {
  if (!token) return;
  if (eventSource) { eventSource.close(); eventSource = null; }
  connDot.classList.remove("online");
  connLabel.textContent = "Connecting";
  let ticket;
  try { ticket = await getAssetTicket(); } catch { return; }
  if (!token) return;
  const es = new EventSource(`/api/events?ticket=${encodeURIComponent(ticket)}`);
  eventSource = es;
  es.onopen = () => {
    connDot.classList.add("online");
    connLabel.textContent = "Connected";
  };
  es.onmessage = (e) => {
    try { renderState(JSON.parse(e.data)); } catch {}
  };
  es.onerror = async () => {
    if (eventSource !== es) return;
    connDot.classList.remove("online");
    connLabel.textContent = "Reconnecting";
    try {
      const res = await fetch("/api/state", { headers: { "X-Remote-Token": token } });
      if (res.status === 401) { clearToken(); return; }
      assetTicket = "";
      assetTicketExpiresAt = 0;
      es.close();
      eventSource = null;
      setTimeout(connectSse, 600);
    } catch {}
  };
}

function renderState(state) {
  const appsChanged = !lastState || (lastState.apps?.length !== state.apps?.length);
  lastState = state;
  ringBtn.classList.toggle("hidden", !state.canRingRemote);

  const np = state.nowPlaying;
  const npEl = document.getElementById("now-playing");
  if (!np) {
    npEl.classList.add("hidden");
  } else {
    npEl.classList.remove("hidden");
    document.getElementById("np-source").textContent = np.sourceApp || "Now Playing";
    document.getElementById("np-title").textContent = np.title || "";
    document.getElementById("np-sub").textContent = np.subtitle || "";
    document.querySelector("#np-play-icon path").setAttribute("d", np.isPlaying ? PAUSE_PATH : PLAY_PATH);
    const art = document.getElementById("np-art");
    if (np.artworkUrl) { art.src = np.artworkUrl; art.classList.remove("hidden"); }
    else { art.classList.add("hidden"); }
    const pct = np.durationMs > 0 ? Math.min(100, (np.positionMs / np.durationMs) * 100) : 0;
    document.getElementById("np-progress").style.width = `${pct}%`;
  }

  if (appsChanged && !document.getElementById("tab-apps").classList.contains("hidden")) {
    renderApps(appsSearch.value);
  }
}

const REPEAT_KEYS = new Set(["DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DEL"]);

document.querySelectorAll("[data-key]").forEach(btn => {
  let pressTimer = null;
  let repeatTimer = null;
  let longPressFired = false;
  const key = btn.dataset.key;
  const clearPress = () => {
    if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; }
    if (repeatTimer) { clearInterval(repeatTimer); repeatTimer = null; }
  };
  btn.addEventListener("pointerdown", () => {
    clearPress();
    longPressFired = false;
    if (REPEAT_KEYS.has(key)) {
      pressTimer = setTimeout(() => {
        longPressFired = true;
        haptic();
        api("/api/key", { key }).catch(() => {});
        repeatTimer = setInterval(() => {
          api("/api/key", { key }).catch(() => {});
        }, 130);
      }, 380);
    } else {
      pressTimer = setTimeout(() => {
        longPressFired = true;
        haptic();
        api("/api/key", { key, longPress: true }).catch(() => {});
      }, 450);
    }
  });
  ["pointerup", "pointercancel", "pointerleave"].forEach(ev => {
    btn.addEventListener(ev, clearPress);
  });
  btn.addEventListener("click", () => {
    clearPress();
    if (longPressFired) {
      longPressFired = false;
      return;
    }
    haptic();
    api("/api/key", { key }).catch(() => {});
  });
});

document.getElementById("np-play").addEventListener("click", () => {
  haptic();
  api("/api/key", { key: "PLAY_PAUSE" }).catch(() => {});
});

document.querySelectorAll("[data-vol]").forEach(btn => {
  btn.addEventListener("click", () => {
    haptic();
    api("/api/volume", { action: btn.dataset.vol }).catch(() => {});
  });
});

ringBtn.addEventListener("click", () => {
  haptic();
  api("/api/remote/ring", {}).catch(() => {});
});

// Segmented tab switcher
document.querySelectorAll(".seg-btn").forEach(btn => {
  btn.addEventListener("click", () => {
    haptic();
    const tab = btn.dataset.tab;
    document.querySelectorAll(".seg-btn").forEach(b => b.classList.toggle("active", b === btn));
    document.getElementById("tab-dpad").classList.toggle("hidden", tab !== "dpad");
    document.getElementById("tab-touchpad").classList.toggle("hidden", tab !== "touchpad");
    document.getElementById("tab-apps").classList.toggle("hidden", tab !== "apps");
    document.getElementById("tab-media").classList.toggle("hidden", tab !== "media");
    document.getElementById("remote-dock").classList.toggle("hidden", tab === "apps" || tab === "media");
    if (tab === "apps") renderApps(appsSearch.value);
    if (tab === "media" && !mediaSearch.value.trim()) loadMediaHome();
  });
});

// Swipe-threshold D-pad (56px) + tap/hold for OK
const touchpad = document.getElementById("touchpad");
let startX = 0, startY = 0, moved = false, padTimer = null, padLongFired = false;
touchpad.addEventListener("touchstart", (e) => {
  const t = e.touches[0];
  startX = t.clientX;
  startY = t.clientY;
  moved = false;
  padLongFired = false;
  if (padTimer) clearTimeout(padTimer);
  padTimer = setTimeout(() => {
    if (!moved) {
      padLongFired = true;
      haptic();
      api("/api/key", { key: "DPAD_CENTER", longPress: true }).catch(() => {});
    }
  }, 450);
});
touchpad.addEventListener("touchmove", (e) => {
  e.preventDefault();
  const t = e.touches[0];
  const dx = t.clientX - startX;
  const dy = t.clientY - startY;
  if (Math.hypot(dx, dy) >= 56) {
    moved = true;
    if (padTimer) { clearTimeout(padTimer); padTimer = null; }
    const key = Math.abs(dx) > Math.abs(dy)
      ? (dx > 0 ? "DPAD_RIGHT" : "DPAD_LEFT")
      : (dy > 0 ? "DPAD_DOWN" : "DPAD_UP");
    startX = t.clientX;
    startY = t.clientY;
    haptic();
    api("/api/key", { key }).catch(() => {});
  }
}, { passive: false });
touchpad.addEventListener("touchend", () => {
  if (padTimer) { clearTimeout(padTimer); padTimer = null; }
  if (!moved && !padLongFired) {
    haptic();
    api("/api/key", { key: "DPAD_CENTER" }).catch(() => {});
  }
});

// Apps grid
const appsGrid = document.getElementById("apps-grid");
const appsSearch = document.getElementById("apps-search");
function renderApps(filter = "") {
  const apps = (lastState?.apps || []).filter(a => a.label.toLowerCase().includes(filter.toLowerCase()));
  appsGrid.innerHTML = "";
  apps.forEach(a => {
    const tile = document.createElement("button");
    tile.className = "m3-app-tile";
    const img = document.createElement("img");
    img.className = "m3-app-icon";
    img.loading = "lazy";
    getAssetTicket().then(t => {
      img.src = `/api/app-icon/${encodeURIComponent(a.packageName)}?ticket=${encodeURIComponent(t)}`;
    }).catch(() => {});
    img.alt = "";
    const name = document.createElement("span");
    name.className = "m3-app-name";
    name.textContent = a.label;
    tile.appendChild(img);
    tile.appendChild(name);
    tile.onclick = () => {
      haptic();
      api("/api/launch", { packageName: a.packageName }).catch(() => {});
    };
    appsGrid.appendChild(tile);
  });
}
appsSearch.oninput = (e) => renderApps(e.target.value);

// Media tab (Continue Watching, Watchlist, Search)
const mediaContent = document.getElementById("media-content");
const mediaSearch = document.getElementById("media-search");
let searchTimer = null;

function createMediaCard(item) {
  const card = document.createElement("button");
  card.className = "media-card";
  const wrap = document.createElement("div");
  wrap.className = "media-poster-wrap";
  if (item.posterUrl) {
    const img = document.createElement("img");
    img.className = "media-poster";
    img.loading = "lazy";
    img.src = item.posterUrl;
    img.alt = "";
    wrap.appendChild(img);
  }
  if (item.progressPct > 0) {
    const track = document.createElement("div");
    track.className = "media-prog-track";
    const fill = document.createElement("div");
    fill.className = "media-prog-fill";
    fill.style.width = `${item.progressPct}%`;
    track.appendChild(fill);
    wrap.appendChild(track);
  }
  const title = document.createElement("div");
  title.className = "media-card-title";
  title.textContent = item.title;
  card.appendChild(wrap);
  card.appendChild(title);
  card.onclick = () => {
    haptic();
    api("/api/media/play", { id: item.id }).catch(() => {});
  };
  return card;
}

function renderMediaHome(data) {
  mediaContent.innerHTML = "";
  const sections = [
    { label: "Continue Watching", items: data.continueWatching || [] },
    { label: "Watchlist", items: data.watchlist || [] }
  ].filter(s => s.items.length > 0);

  if (sections.length === 0) {
    mediaContent.innerHTML = '<div class="media-empty">No recent media</div>';
    return;
  }
  sections.forEach(s => {
    const h = document.createElement("div");
    h.className = "media-section-title";
    h.textContent = s.label;
    const row = document.createElement("div");
    row.className = "media-grid";
    s.items.forEach(item => row.appendChild(createMediaCard(item)));
    mediaContent.appendChild(h);
    mediaContent.appendChild(row);
  });
}

function showMediaLoading() {
  mediaContent.innerHTML = '<div class="m3-loader-wrap"><div class="m3-spinner"></div></div>';
}

function loadMediaHome() {
  showMediaLoading();
  api("/api/media/home").then(r => r.json()).then(renderMediaHome).catch(() => {
    mediaContent.innerHTML = '<div class="media-empty">Failed to load</div>';
  });
}

mediaSearch.oninput = (e) => {
  clearTimeout(searchTimer);
  const q = e.target.value.trim();
  if (q.length < 2) {
    loadMediaHome();
    return;
  }
  showMediaLoading();
  searchTimer = setTimeout(() => {
    api(`/api/media/search?q=${encodeURIComponent(q)}`)
      .then(r => r.json())
      .then(items => {
        mediaContent.innerHTML = "";
        if (!items.length) {
          mediaContent.innerHTML = '<div class="media-empty">No results</div>';
          return;
        }
        const grid = document.createElement("div");
        grid.className = "media-grid";
        items.forEach(item => grid.appendChild(createMediaCard(item)));
        mediaContent.appendChild(grid);
      })
      .catch(() => {});
  }, 300);
};

// Keyboard modal
const kbdModal = document.getElementById("kbd-modal");
const kbdInput = document.getElementById("kbd-input");
document.getElementById("kbd-open-btn").onclick = () => { kbdModal.classList.remove("hidden"); kbdInput.focus(); };
kbdModal.onclick = (e) => { if (e.target === kbdModal) kbdModal.classList.add("hidden"); };
document.getElementById("kbd-send-btn").onclick = () => {
  if (!kbdInput.value) return;
  haptic();
  api("/api/text", { text: kbdInput.value }).catch(() => {});
  kbdInput.value = "";
  kbdModal.classList.add("hidden");
};

pairBtn.onclick = () => pair(pinInput.value.trim());

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && token) {
    connectSse();
    api("/api/state").then(r => r.json()).then(renderState).catch(() => {});
  }
});

(async function init() {
  const params = new URLSearchParams(window.location.search);
  const qrPin = params.get("pair");
  if (qrPin) {
    history.replaceState({}, "", window.location.pathname);
    if (await pair(qrPin)) return;
  }
  if (token) showRemote();
  else showPair();
})();
