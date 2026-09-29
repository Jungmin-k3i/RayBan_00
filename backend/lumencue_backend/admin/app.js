const state = { key: sessionStorage.getItem("lumencueAdminKey") || "", overview: null };
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const authShell = $("#authShell");
const appShell = $("#appShell");
const dialog = $("#operationsDialog");
const panelTitles = { package: "공연 패키지 등록", notice: "긴급 공지 발행", ticket: "티켓 체크인 반영", logs: "HUD 전송 로그" };

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[char]));
}

function formatTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(date);
}

async function api(path, options = {}) {
  const headers = { "X-Admin-Key": state.key, ...(options.headers || {}) };
  if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
  const response = await fetch(path, { ...options, headers });
  const payload = await response.json().catch(() => ({ error: { message: "서버 응답을 읽을 수 없습니다." } }));
  if (!response.ok) {
    const error = new Error(payload.error?.message || `요청 실패 (${response.status})`);
    error.code = payload.error?.code;
    error.details = payload.error?.details;
    error.status = response.status;
    throw error;
  }
  return payload.data;
}

async function loadOverview({ silent = false } = {}) {
  if (!silent) $("#syncStatus").textContent = "운영 데이터를 동기화하는 중입니다.";
  const overview = await api("/v1/admin/overview");
  state.overview = overview;
  renderOverview();
  populateEventSelects();
  $("#syncStatus").textContent = `${formatTime(overview.generatedAt)} 기준 · 서버 연결 정상`;
  return overview;
}

function renderOverview() {
  const { metrics, events } = state.overview;
  const metricItems = [
    [metrics.publishedEvents, "배포 중인 공연"],
    [metrics.awaitingApproval, "승인 대기 버전"],
    [metrics.activeNotices, "활성 긴급 공지"],
    [metrics.dispatchCount, "누적 HUD 전송"],
  ];
  $("#metrics").innerHTML = metricItems.map(([value, label], index) => `<article class="metric"><strong>${escapeHtml(value)}</strong><span>${label}</span>${index === 0 ? "<em aria-hidden=\"true\"></em>" : ""}</article>`).join("");
  if (!events.length) {
    $("#eventList").innerHTML = `<div class="empty-state"><strong>아직 등록된 공연이 없습니다.</strong>첫 공연 패키지 JSON을 등록하면 배포 파이프라인이 시작됩니다.</div>`;
    return;
  }
  $("#eventList").innerHTML = events.map((event, index) => renderEvent(event, index)).join("");
}

function renderEvent(event, index) {
  const notice = event.activeNotice ? `<div class="notice-strip ${event.activeNotice.severity === "Critical" ? "critical" : ""}"><span><strong>${escapeHtml(event.activeNotice.severity)}</strong>${escapeHtml(event.activeNotice.messageKo)}</span><time>만료 ${formatTime(event.activeNotice.expiresAt)}</time></div>` : "";
  const versions = event.versions.map((version) => {
    const canApprove = version.status === "draft";
    const canPublish = version.status === "approved" && version.releaseChannel !== "rehearsal";
    const summary = version.changeSummary || (version.baseVersion ? `v${version.baseVersion} 기준 변경` : "최초 등록 버전");
    return `<div class="version-row">
      <span class="version-label">v${escapeHtml(version.version)}</span>
      <span class="status-pill ${escapeHtml(version.status)}">${statusLabel(version.status)}</span>
      <span class="channel">${escapeHtml(version.releaseChannel)}</span>
      <span class="version-summary" title="${escapeHtml(summary)}">${escapeHtml(summary)}</span>
      <span class="version-actions">
        ${canApprove ? `<button class="button ghost" type="button" data-approve="${escapeHtml(event.id)}" data-version="${version.version}">승인</button>` : ""}
        ${canPublish ? `<button class="button primary" type="button" data-publish="${escapeHtml(event.id)}" data-version="${version.version}">배포</button>` : ""}
        ${version.status === "published" ? `<button class="button ghost" type="button" data-notice-event="${escapeHtml(event.id)}">공지 발행</button>` : ""}
      </span>
    </div>`;
  }).join("");
  return `<article class="event-card">
    <div class="event-summary">
      <div class="event-title"><span class="event-index">${String(index + 1).padStart(2, "0")}</span><div><h3>${escapeHtml(event.title)}</h3><p>${escapeHtml(event.id)}</p></div></div>
      <div class="datum"><strong>${escapeHtml(event.venue)}</strong><span>${escapeHtml(event.showDate)}</span></div>
      <div class="datum"><strong>${event.checkedInTickets}명</strong><span>체크인 티켓</span></div>
      <div class="datum"><strong>${event.dispatchCount}건</strong><span>HUD 전송 기록</span></div>
    </div>${notice}<div class="versions">${versions}</div>
  </article>`;
}

function statusLabel(status) {
  return ({ draft: "검수 전", approved: "승인됨", published: "배포 중", superseded: "이전 버전" })[status] || status;
}

function populateEventSelects() {
  const events = state.overview?.events || [];
  $$('[data-event-select]').forEach((select) => {
    const current = select.value;
    const includeAll = select.id === "logEventFilter";
    select.innerHTML = `${includeAll ? '<option value="">전체 공연</option>' : '<option value="">공연 선택</option>'}${events.map((event) => `<option value="${escapeHtml(event.id)}">${escapeHtml(event.title)} · ${escapeHtml(event.id)}</option>`).join("")}`;
    if ([...select.options].some((option) => option.value === current)) select.value = current;
  });
}

function openPanel(name, eventId = "") {
  $$(".operation-panel", dialog).forEach((panel) => panel.hidden = panel.dataset.panel !== name);
  $("#dialogTitle").textContent = panelTitles[name];
  if (eventId) {
    const select = $(`[data-panel="${name}"] [name="eventId"]`, dialog);
    if (select) select.value = eventId;
  }
  dialog.showModal();
}

function setFormMessage(form, message, type = "") {
  const target = $(".form-message", form);
  target.textContent = message;
  target.className = `form-message ${type}`;
}

function setBusy(form, busy) {
  $$('button[type="submit"]', form).forEach((button) => button.disabled = busy);
}

function showToast(message, type = "") {
  const toast = $("#toast");
  toast.textContent = message;
  toast.className = `toast visible ${type}`;
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.className = "toast", 3200);
}

function describeError(error) {
  if (error.details?.length) return `${error.message} ${error.details.slice(0, 2).map((item) => `${item.path}: ${item.message}`).join(" · ")}`;
  return error.message;
}

$("#authForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  state.key = new FormData(event.currentTarget).get("adminKey").trim();
  $("#authError").textContent = "";
  try {
    await loadOverview();
    sessionStorage.setItem("lumencueAdminKey", state.key);
    authShell.hidden = true;
    appShell.hidden = false;
  } catch (error) {
    state.key = "";
    $("#authError").textContent = error.code === "ADMIN_API_DISABLED" ? "서버에서 관리자 API가 비활성화되어 있습니다." : "API 키를 확인해 주세요.";
  }
});

$("#disconnectButton").addEventListener("click", () => {
  sessionStorage.removeItem("lumencueAdminKey");
  state.key = "";
  appShell.hidden = true;
  authShell.hidden = false;
  $("#adminKey").value = "";
  $("#adminKey").focus();
});

$("#refreshButton").addEventListener("click", async () => {
  try { await loadOverview(); showToast("최신 운영 상태로 갱신했습니다."); } catch (error) { showToast(describeError(error), "error"); }
});

$("#operatorName").value = sessionStorage.getItem("lumencueOperator") || "";
$("#operatorName").addEventListener("change", (event) => sessionStorage.setItem("lumencueOperator", event.target.value.trim()));

$$('[data-open-panel]').forEach((button) => button.addEventListener("click", () => openPanel(button.dataset.openPanel)));
$("#closeDialogButton").addEventListener("click", () => dialog.close());
dialog.addEventListener("click", (event) => { if (event.target === dialog) dialog.close(); });

$("#eventList").addEventListener("click", async (event) => {
  const approve = event.target.closest("[data-approve]");
  const publish = event.target.closest("[data-publish]");
  const notice = event.target.closest("[data-notice-event]");
  if (notice) return openPanel("notice", notice.dataset.noticeEvent);
  if (!approve && !publish) return;
  const button = approve || publish;
  const operator = $("#operatorName").value.trim();
  if (approve && !operator) { $("#operatorName").focus(); showToast("승인 전에 현재 운영자 이름을 입력해 주세요.", "error"); return; }
  if (publish && !window.confirm(`${button.dataset.approve || button.dataset.publish} v${button.dataset.version}을 실제 앱에 배포할까요?`)) return;
  button.disabled = true;
  try {
    const eventId = button.dataset.approve || button.dataset.publish;
    const action = approve ? "approve" : "publish";
    const body = approve ? JSON.stringify({ approvedBy: operator }) : JSON.stringify({});
    await api(`/v1/admin/concert-packages/${encodeURIComponent(eventId)}/versions/${button.dataset.version}/${action}`, { method: "POST", body });
    await loadOverview({ silent: true });
    showToast(approve ? "패키지를 승인했습니다." : "새 버전을 배포했습니다.");
  } catch (error) { button.disabled = false; showToast(describeError(error), "error"); }
});

$("#packageForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  const file = data.get("packageFile");
  setBusy(form, true); setFormMessage(form, "JSON을 검증하는 중입니다.");
  try {
    const packageData = JSON.parse(await file.text());
    const baseVersion = data.get("baseVersion");
    await api("/v1/admin/concert-packages", { method: "POST", body: JSON.stringify({ package: packageData, version: Number(data.get("version")), releaseChannel: data.get("releaseChannel"), baseVersion: baseVersion ? Number(baseVersion) : null, changeSummary: data.get("changeSummary") }) });
    setFormMessage(form, "검증을 통과해 draft로 등록했습니다.", "success");
    await loadOverview({ silent: true });
    setTimeout(() => dialog.close(), 700);
  } catch (error) { setFormMessage(form, error instanceof SyntaxError ? "올바른 JSON 파일이 아닙니다." : describeError(error), "error"); }
  finally { setBusy(form, false); }
});

$("#noticeForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget; const data = new FormData(form); const operator = $("#operatorName").value.trim();
  if (!operator) { setFormMessage(form, "상단의 현재 운영자 이름을 먼저 입력해 주세요.", "error"); return; }
  setBusy(form, true); setFormMessage(form, "공지 우선순위를 확인하는 중입니다.");
  try {
    await api(`/v1/admin/concerts/${encodeURIComponent(data.get("eventId"))}/emergency-notices`, { method: "POST", body: JSON.stringify({ severity: data.get("severity"), messageKo: data.get("messageKo"), messageEn: data.get("messageEn"), createdBy: operator, expiresAt: new Date(data.get("expiresAt")).toISOString() }) });
    setFormMessage(form, "긴급 공지를 발행했습니다.", "success"); await loadOverview({ silent: true }); setTimeout(() => dialog.close(), 700);
  } catch (error) { setFormMessage(form, describeError(error), "error"); }
  finally { setBusy(form, false); }
});

$("#ticketForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget; const data = new FormData(form);
  setBusy(form, true); setFormMessage(form, "티켓 상태를 반영하는 중입니다.");
  try {
    await api("/v1/admin/tickets", { method: "POST", body: JSON.stringify({ eventId: data.get("eventId"), provider: data.get("provider"), ticketId: data.get("ticketId"), checkedIn: data.get("checkedIn") === "on", externalSubject: data.get("externalSubject") || null }) });
    setFormMessage(form, "티켓 상태를 반영했습니다.", "success"); await loadOverview({ silent: true }); form.elements.ticketId.value = "";
  } catch (error) { setFormMessage(form, describeError(error), "error"); }
  finally { setBusy(form, false); }
});

$("#loadLogsButton").addEventListener("click", async () => {
  const eventId = $("#logEventFilter").value;
  $("#logList").innerHTML = '<p class="empty-copy">전송 기록을 불러오는 중입니다.</p>';
  try {
    const data = await api(`/v1/admin/device-dispatch-logs?limit=80${eventId ? `&eventId=${encodeURIComponent(eventId)}` : ""}`);
    $("#logList").innerHTML = data.items.length ? data.items.map((log) => `<article class="log-item"><span class="log-state ${log.accepted ? "" : "failed"}">${log.accepted ? "ACCEPTED" : "FAILED"}</span><div class="log-main"><strong>${escapeHtml(log.route)} · ${escapeHtml(log.rendererName)}</strong><small>${escapeHtml(log.eventId)} / ${escapeHtml(log.documentId)}${log.errorCode ? ` · ${escapeHtml(log.errorCode)}` : ""}</small></div><time class="log-time">${formatTime(log.occurredAt)}</time></article>`).join("") : '<p class="empty-copy">조건에 맞는 전송 기록이 없습니다.</p>';
  } catch (error) { $("#logList").innerHTML = `<p class="empty-copy">${escapeHtml(describeError(error))}</p>`; }
});

function setDefaultExpiry() {
  const date = new Date(Date.now() + 60 * 60 * 1000);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  $('#noticeForm [name="expiresAt"]').value = local;
}
setDefaultExpiry();

if (state.key) {
  $("#adminKey").value = state.key;
  loadOverview().then(() => { authShell.hidden = true; appShell.hidden = false; }).catch(() => { sessionStorage.removeItem("lumencueAdminKey"); state.key = ""; });
}
