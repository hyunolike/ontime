/*
 * 길벗 체험판.
 *
 * 판단은 하지 않는다. 실제 앱 코드(web-export 모듈)로 만든 주행 기록을 재생하고, 들를지 묻는 순간에 사용자가 고른
 * 갈래의 기록으로 넘어간다. 모든 갈래는 답하는 순간까지 프레임이 같아서 같은 위치에서 이어 붙일 수 있다.
 */
(() => {
  "use strict";

  const ASK_SECONDS = 10; // 앱과 같다. 답이 없으면 지나간다.
  const $ = (sel) => document.querySelector(sel);
  const screen = $("#screen");

  const s = {
    index: null, places: null, byId: {}, paths: {},
    key: "none", guide: null, yes: new Set(), answered: new Set(),
    idx: 0, mode: "ready", timer: null, askStart: 0, askDeadline: 0, askTimer: null,
    charging: false, voice: false, spokenKey: "", speed: 1,
  };

  // ---------- 데이터 ----------
  async function load() {
    const get = (f) => fetch(`data/${f}`).then((r) => r.json());
    s.index = await get("index.json");
    s.places = await get("places.json");
    [...s.places.places, s.places.destination].forEach((p) => (s.byId[p.id] = p));
    const all = await Promise.all(s.index.paths.map((k) => get(`path-${k}.json`)));
    all.forEach((p) => (s.paths[p.key] = p.frames));
    initMap();
    reset();
  }

  const frames = () => s.paths[s.key];
  const frame = () => frames()[s.idx];
  const keyOf = (guide, yes) => {
    const parts = [...yes].sort().join("+") || "none";
    return guide ? `g-${guide}` + (yes.size ? `+${parts}` : "") : parts;
  };

  // ---------- 진행 ----------
  function reset() {
    stop();
    clearAsk();
    s.key = "none"; s.guide = null; s.yes = new Set(); s.answered = new Set();
    s.idx = 0; s.mode = "ready"; s.charging = false; s.spokenKey = "";
    if (window.speechSynthesis) speechSynthesis.cancel();
    render();
  }

  function play() {
    stop();
    s.timer = setInterval(tick, s.index.frameMillis / s.speed);
  }
  function stop() { if (s.timer) clearInterval(s.timer); s.timer = null; }

  function depart() {
    s.mode = "driving";
    s.charging = false;
    play();
    render();
  }

  function tick() {
    if (s.idx >= frames().length - 1) return;
    s.idx += 1;
    const f = frame();
    if (f.n && f.n.ph === "ASKING" && !s.answered.has(f.n.id)) return startAsk(f.n.id);
    if (f.stop === "waypoint") { s.mode = "parked"; stop(); }
    else if (f.stop === "end") { s.mode = "end"; stop(); }
    render();
  }

  function startAsk(id) {
    stop();
    s.mode = "asking";
    s.askStart = s.idx;
    s.askDeadline = Date.now() + ASK_SECONDS * 1000;
    s.askTimer = setInterval(() => {
      if (Date.now() >= s.askDeadline) answer(id, false);
      else updateCountdown();
    }, 200);
    render();
  }
  function clearAsk() { if (s.askTimer) clearInterval(s.askTimer); s.askTimer = null; }

  function answer(id, yes) {
    if (s.mode !== "asking") return;
    clearAsk();
    s.answered.add(id);
    if (yes) {
      const next = new Set(s.yes); next.add(id);
      const key = keyOf(s.guide, next);
      if (s.paths[key]) { s.yes = next; s.key = key; }
    }
    // 기록에서 답한 프레임으로 넘어간다.
    s.idx = Math.min(s.askStart + s.index.answerFrames, frames().length - 1);
    s.mode = "driving";
    play();
    render();
  }

  function guide(id) {
    if (s.mode !== "ready" || s.guide) return;
    const key = keyOf(id, new Set());
    if (!s.paths[key]) return;
    s.guide = id; s.key = key;
    render();
  }

  function resume() {
    if (s.mode !== "parked") return;
    s.mode = "driving";
    s.charging = false;
    play();
    render();
  }

  // ---------- 화면 ----------
  const sideWord = (side) => (side === "L" ? "왼쪽" : "오른쪽");
  const km = (m) => (m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`);
  const minutes = (sec) => `${Math.max(0, Math.ceil(sec / 60))}분`;
  const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

  function photo(id, { side, beacon, caption, cls = "" } = {}) {
    const p = s.byId[id];
    const img = p && p.photo ? `<img src="img/${p.photo}" alt="${esc(p.name)}">` : "";
    const credit = p && p.credit ? `<span class="credit">사진 ${esc(p.credit)}</span>` : "";
    const beaconHtml = beacon ? `<div class="beacon ${side === "L" ? "l" : "r"}"></div><div class="arrow ${side === "L" ? "l" : "r"}" aria-label="${sideWord(side)}">${side === "L" ? "←" : "→"}</div>` : "";
    return `<div class="photo ${cls}">${img}${credit}${beaconHtml}${caption ? `<div class="caption">${caption}</div>` : ""}</div>`;
  }

  function header(f, trailing) {
    return `<div class="hdr"><span class="trip">${esc(s.places.destination.name)}까지 ${minutes(f.rem)}</span><span class="chip">▷ 예시 경로 재생</span><span>${trailing}</span></div>`;
  }

  function rail(f) {
    const aheadIds = new Set(f.ah.map((a) => a[0]));
    const passed = f.told.filter((id) => !aheadIds.has(id));
    const dot = (id) => {
      if (f.st.includes(id)) return "visited";
      if (f.wp === id) return "wp";
      if (f.told.includes(id)) return "told";
      return "";
    };
    const stop = (id) => `<span class="dot ${dot(id)}"></span><span class="stop">${esc(s.byId[id].name)}</span><span class="seg"></span>`;
    return `<div class="rail">${passed.map(stop).join("")}<span class="car">🚘</span><span class="seg"></span>${f.ah.map((a) => stop(a[0])).join("")}<span class="stop">⛳ ${esc(s.places.destination.name)}</span></div>`;
  }

  function extraLine(p) {
    if (!p.extra) return "";
    return `들르면 약 ${p.extra}분 더` + (p.stay ? ` (관람 ${p.stay}분 포함)` : "");
  }

  function drivingScreen(f) {
    let body;
    const n = f.n;
    if (f.q) {
      const next = f.ah.find((a) => !f.told.includes(a[0]));
      body = `<div class="quiet"><div class="big">⏸ 잠시 쉽니다</div><div class="title">${km(f.q[0])} 앞 ${esc(f.q[1])}. 길 안내가 먼저입니다</div>${next ? `<div class="body">회전을 지나면 ${esc(s.byId[next[0]].name)} 이야기를 들려드립니다</div>` : ""}</div>`;
    } else if (n) {
      const p = s.byId[n.id];
      let tail = "";
      if (n.ph === "TELLING") {
        tail = `${n.side === "R" ? `<div class="seat">조수석에서 잘 보여요</div>` : ""}<div class="body">${esc(n.story || p.story)}</div>`;
      } else if (n.ph === "ASKING") {
        const live = s.mode === "asking";
        tail = `<div class="title">들러 볼까요?</div>${p.highlight ? `<div class="body">${esc(p.highlight)}</div>` : ""}<div class="label">${extraLine(p)}</div>
          <div class="answer"><button class="btn route" data-act="yes" data-id="${n.id}" ${live ? "" : "disabled"}>들르기</button><button class="btn secondary" data-act="no" data-id="${n.id}" ${live ? "" : "disabled"}>지나가기</button></div>
          ${live ? `<div class="countdown"><div id="cd" style="width:100%"></div></div>` : ""}`;
      } else if (n.ph === "ADDED") {
        tail = `<div class="added">＋ ${esc(n.text)}</div>`;
      } else {
        tail = `<div class="body">${esc(n.text)}</div>`;
      }
      body = `${photo(n.id, { side: n.side, beacon: true })}<div class="lead">💬 ${esc(n.lead)} ${sideWord(n.side)} 창밖</div><div class="name ${p.name.length > 5 ? "small" : ""}">${esc(p.name)}</div>${tail}`;
    } else {
      const next = f.ah.find((a) => !f.told.includes(a[0]));
      const heading = f.wp && !f.st.includes(f.wp) ? f.wp : null;
      if (next) {
        body = `${photo(next[0])}<div class="label">다음 이야기</div><div class="name small">${esc(s.byId[next[0]].name)}</div><div class="body">직선 ${km(next[1])}. 가까워지면 들려드립니다</div>`;
      } else if (heading) {
        body = `${photo(heading)}<div class="added">들르러 가는 길</div><div class="name small">${esc(s.byId[heading].name)}</div><div class="body">차를 세우면 방문 도장이 찍힙니다</div>`;
      } else {
        body = `${photo(s.places.destination.id)}<div class="label">목적지</div><div class="name small">${esc(s.places.destination.name)}</div><div class="body">${minutes(f.rem)} 남았습니다. 오늘 이야기는 모두 들었습니다</div>`;
      }
    }
    return `${header(f, "D 주행 중")}${body}${rail(f)}`;
  }

  function stoppedScreen(f) {
    const dest = s.places.destination;
    let heroId = dest.id, label = `목적지 · ${minutes(f.rem)}`;
    if (s.mode === "parked") { heroId = f.wp || f.st[f.st.length - 1]; label = `<span style="color:var(--positive)">도착했습니다</span>`; }
    else if (s.mode === "end") label = "목적지에 도착했습니다";
    else if (f.wp) { heroId = f.wp; label = "들를 곳"; }
    const hero = photo(heroId, { cls: "hero", caption: `<div class="label" style="color:var(--text)">${label}</div><div class="title">${esc(s.byId[heroId].name)}</div>` });

    const stopIds = s.places.places.filter((p) => p.canStop).map((p) => p.id);
    const stamps = `<div class="card stamps"><div style="flex:1"><div class="title">방문 도장 ${f.st.length}/${stopIds.length}</div><div class="label">차를 세워 들른 곳에만 찍힙니다</div></div>${stopIds.map((id) => `<div><div class="stamp ${f.st.includes(id) ? "on" : ""}">✓</div><div class="stamp-name">${esc(s.byId[id].name)}</div></div>`).join("")}</div>`;

    let walk = "";
    if (s.charging && f.walk) {
      walk = `<div class="card"><div class="walk"><span class="title">⚡ 걸어서 둘러볼 곳</span><span class="chip acc">충전 시험</span></div>${f.walk.length ? f.walk.map(([id, m]) => `<div class="place" style="margin-top:8px"><div class="thumb">${s.byId[id].photo ? `<img src="img/${s.byId[id].photo}" alt="">` : ""}</div><div class="info"><div>${esc(s.byId[id].name)}</div><div class="label">${esc(s.byId[id].highlight || "")}</div></div><div class="label">🚶 걸어서 ${m}분</div></div>`).join("") : `<div class="label">걸어서 20분 안에 둘러볼 곳이 없습니다</div>`}</div>`;
    }

    let action = "";
    if (s.mode === "ready") action = `<button class="btn primary" data-act="depart">▷ 송파 코스 출발</button>`;
    if (s.mode === "parked") action = `<button class="btn route wide" data-act="resume">${esc(dest.name)}으로 계속 안내</button>`;

    const aheadMap = new Map(f.ah.map((a) => [a[0], a]));
    const rows = s.places.places.map((p) => {
      const a = aheadMap.get(p.id);
      const where = a ? (a[1] === 0 ? "지금 여기" : `직선 ${km(a[1])}`) : "지나왔습니다";
      const chip = f.st.includes(p.id) ? `<span class="chip pos">다녀옴</span>` : f.wp === p.id ? `<span class="chip info">들르러 가는 중</span>` : f.told.includes(p.id) ? `<span class="chip acc">들음</span>` : "";
      const canGuide = s.mode === "ready" && !s.guide && p.canStop && a;
      return `<div class="card place"><div class="thumb">${p.photo ? `<img src="img/${p.photo}" alt="">` : ""}</div><div class="info"><div class="title" style="font-size:17px">${esc(p.name)} ${chip}</div><div class="label">${where}${p.extra ? ` · ${extraLine(p)}` : ""}</div></div>${canGuide ? `<button class="guide" data-act="guide" data-id="${p.id}">여기로 안내</button>` : ""}</div>`;
    }).join("");

    return `${header(f, "P 정차 중")}${hero}${stamps}${walk}${action}<div class="title">${esc(dest.name)} 가는 길, 이야기 ${s.places.places.length}곳</div><div class="list">${rows}</div>`;
  }

  function render() {
    const f = frame();
    const stopped = s.mode === "ready" || s.mode === "parked" || s.mode === "end";
    screen.innerHTML = stopped ? stoppedScreen(f) : drivingScreen(f);
    screen.querySelectorAll(".photo.hero").forEach((el) => (el.style.flex = "0 0 34%"));
    $("#charge").disabled = !(stopped && f.walk);
    $("#charge").textContent = s.charging ? "충전 끝" : "충전 시작";
    updateMap(f);
    speak(f);
  }

  function updateCountdown() {
    const el = document.getElementById("cd");
    if (el) el.style.width = `${Math.max(0, (s.askDeadline - Date.now()) / (ASK_SECONDS * 10))}%`;
  }

  // ---------- 해설 듣기(브라우저 음성 합성) ----------
  function speak(f) {
    if (!s.voice || !window.speechSynthesis || !f.n) return;
    const key = `${f.n.id}:${f.n.ph}`;
    if (key === s.spokenKey) return;
    s.spokenKey = key;
    const text = f.n.ph === "TELLING" ? f.n.text : f.n.ph === "ASKING" ? "들러 볼까요?" : f.n.text;
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = "ko-KR";
    u.rate = 1.1;
    speechSynthesis.speak(u);
  }

  // ---------- 지도 ----------
  let map, car, line, markers = {};
  function initMap() {
    map = L.map("map", { zoomControl: true, attributionControl: true });
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 18,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> 기여자',
    }).addTo(map);
    const route = L.polyline(s.places.route, { color: "#8a8f98", weight: 6, opacity: 0.9 }).addTo(map);
    map.fitBounds(route.getBounds(), { padding: [20, 20] });
    [...s.places.places, s.places.destination].forEach((p) => {
      const icon = L.divIcon({ className: "markers", html: `<div class="poi" id="m-${p.id}">${esc(p.name)}</div>`, iconSize: null });
      markers[p.id] = L.marker([p.lat, p.lon], { icon, keyboard: false }).addTo(map);
    });
    car = L.marker(s.places.route[0], { icon: L.divIcon({ className: "", html: `<div class="carmark" id="carmark"><span></span></div>`, iconSize: [26, 26], iconAnchor: [13, 13] }), zIndexOffset: 1000 }).addTo(map);
    line = L.polyline([], { color: "#ffc857", weight: 3, dashArray: "6 6" }).addTo(map);
  }

  function updateMap(f) {
    if (!map) return;
    car.setLatLng([f.lat, f.lon]);
    const mark = document.getElementById("carmark");
    if (mark && f.h != null) mark.style.transform = `rotate(${f.h}deg)`;
    Object.keys(markers).forEach((id) => {
      const el = document.getElementById(`m-${id}`);
      if (!el) return;
      el.className = "poi" + (f.n && f.n.id === id ? " hot" : "") + (f.st.includes(id) ? " done" : "") + (f.wp === id && !f.st.includes(id) ? " wp" : "");
    });
    if (f.n) {
      const p = s.byId[f.n.id];
      line.setLatLngs([[f.lat, f.lon], [p.lat, p.lon]]);
    } else {
      line.setLatLngs([]);
    }
  }

  // ---------- 입력 ----------
  screen.addEventListener("click", (e) => {
    const b = e.target.closest("[data-act]");
    if (!b) return;
    const act = b.dataset.act;
    if (act === "depart") depart();
    if (act === "resume") resume();
    if (act === "guide") guide(b.dataset.id);
    if (act === "yes") answer(b.dataset.id, true);
    if (act === "no") answer(b.dataset.id, false);
  });
  $("#restart").addEventListener("click", reset);
  $("#charge").addEventListener("click", () => { s.charging = !s.charging; render(); });
  $("#voice").addEventListener("change", (e) => { s.voice = e.target.checked; if (!s.voice && window.speechSynthesis) speechSynthesis.cancel(); });
  $("#speed").addEventListener("change", (e) => { s.speed = Number(e.target.value); if (s.timer) play(); });

  load().catch((err) => {
    screen.innerHTML = `<div class="body">체험판 데이터를 불러오지 못했습니다. ${esc(err.message)}</div>`;
  });
})();
