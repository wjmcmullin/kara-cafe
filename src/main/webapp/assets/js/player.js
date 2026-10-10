const API_BASE = "/kara-cafe/api";

let token = null;
let roomId = null;
let roomPollTimer = null;
let currentMediaPath = null;
let sessionInfo = null;
let inviteQrDataUrl = null;
let isPopoutMode = false;
let qrOpacityPercent = 55;
let isPromotingFullscreen = false;
const isFirefox = /firefox/i.test(navigator.userAgent);

document.addEventListener("DOMContentLoaded", async () => {
    const params = new URLSearchParams(window.location.search);
    const tokenFromQuery = params.get("token");
    roomId = params.get("id");
    isPopoutMode = params.get("popout") === "1";

    if (isPopoutMode) {
        document.body.classList.add("player-popout-mode");
    }

    if (tokenFromQuery) {
        localStorage.setItem("karaToken", tokenFromQuery);
        params.delete("token");
        const sanitizedQuery = params.toString();
        const cleanUrl = `${window.location.pathname}${sanitizedQuery ? `?${sanitizedQuery}` : ""}`;
        window.history.replaceState({}, "", cleanUrl);
    }

    token = localStorage.getItem("karaToken");
    if (!token || !roomId) {
        showStatus("Missing room ID or token.", true);
        return;
    }

    setBackLink(roomId);
    bindControls();

    document.getElementById("playerLogoutButton")?.addEventListener("click", async () => {
        try {
            await apiRequest(`/auth/logout`, { method: "DELETE" });
        } catch {
        } finally {
            localStorage.removeItem("karaToken");
            window.location.replace("/kara-cafe/");
        }
    });

    try {
        sessionInfo = await loadSession();
        const role = sessionInfo?.role;
        if (role !== "ADMIN" && role !== "SUPER_ADMIN") {
            window.location.replace(`/kara-cafe/karaoke-room/room?id=${encodeURIComponent(roomId)}`);
            return;
        }

        await enterRoom(roomId);
        await refreshRoom();
        roomPollTimer = window.setInterval(refreshRoom, 3000);
    } catch (error) {
        showStatus(error.message, true);
    }
});

window.addEventListener("beforeunload", () => {
    if (roomPollTimer) {
        window.clearInterval(roomPollTimer);
    }
});

function bindControls() {
    document.getElementById("playQueueButton")?.addEventListener("click", async () => {
        try {
            await playOrLoadNext();
        } catch (error) {
            showStatus(error.message, true);
        }
    });

    const fullscreenButton = document.getElementById("fullscreenPlayerButton");
    fullscreenButton?.addEventListener("click", openFullscreenPlayer);

    const popoutButton = document.getElementById("popoutPlayerButton");
    if (isPopoutMode) {
        popoutButton?.classList.add("hidden");
    }
    popoutButton?.addEventListener("click", popOutPlayerWindow);

    document.getElementById("showInviteQrToggle")?.addEventListener("change", async (event) => {
        const shouldShow = Boolean(event.target?.checked);
        await toggleInviteQrWatermark(shouldShow);
    });

    const opacitySlider = document.getElementById("watermarkOpacitySlider");
    if (opacitySlider) {
        qrOpacityPercent = Number(opacitySlider.value || 55);
        applyQrWatermarkOpacity();
    }
    opacitySlider?.addEventListener("input", (event) => {
        qrOpacityPercent = Number(event.target?.value || 55);
        applyQrWatermarkOpacity();
    });

    const player = document.getElementById("karaokePlayer");
    if (isFirefox && player) {
        player.removeAttribute("controls");
        showStatus("Firefox mode: use the page Fullscreen button to keep QR watermark visible.");
    }

    document.getElementById("pauseResumeButton")?.addEventListener("click", async () => {
        try {
            await togglePauseResume();
        } catch (error) {
            showStatus(error.message, true);
        }
    });

    player?.addEventListener("play", syncPauseResumeLabel);
    player?.addEventListener("pause", syncPauseResumeLabel);
    player?.addEventListener("ended", async () => {
        try {
            await handleSongEnded();
            syncPauseResumeLabel();
        } catch (error) {
            showStatus(error.message, true);
        }
    });

    document.addEventListener("fullscreenchange", handleFullscreenElementChange);
    document.addEventListener("webkitfullscreenchange", handleFullscreenElementChange);

    syncPauseResumeLabel();
}

async function apiRequest(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/json",
            ...(options.headers || {})
        }
    });

    if (response.status === 401) {
        localStorage.removeItem("karaToken");
        window.location.replace("/kara-cafe/");
        throw new Error("Authentication required");
    }

    return response;
}

async function loadSession() {
    const response = await apiRequest(`/auth/session`);
    if (!response.ok) {
        throw new Error("Unable to load session.");
    }
    return response.json();
}

async function enterRoom(targetRoomId) {
    const response = await apiRequest(`/rooms/${targetRoomId}/enter`, { method: "POST" });
    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Unable to enter room.");
    }

    return response.json();
}

async function refreshRoom() {
    const response = await apiRequest(`/rooms/${roomId}`);
    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Unable to load room state.");
    }

    const room = await response.json();
    renderRoom(room);
}

function renderRoom(room) {
    document.getElementById("playerRoomName").textContent = `${room.name} - Player`;

    const queue = room.songRequests || [];
    const queueList = document.getElementById("playerQueue");
    queueList.innerHTML = "";

    if (!queue.length) {
        const li = document.createElement("li");
        li.className = "subtext";
        li.textContent = "Queue is empty.";
        queueList.appendChild(li);
    } else {
        const fragment = document.createDocumentFragment();
        queue.forEach((entry, index) => {
            const li = document.createElement("li");
            li.innerHTML = `${index + 1}. ${escapeHtml(entry.song)} <span class="subtext">(${escapeHtml(entry.requester)})</span>`;
            fragment.appendChild(li);
        });
        queueList.appendChild(fragment);
    }

    renderPlayer(room.currentSong);
}

function renderPlayer(currentSong) {
    const nowPlaying = document.getElementById("playerNowPlaying");
    const player = document.getElementById("karaokePlayer");

    if (!currentSong) {
        currentMediaPath = null;
        nowPlaying.textContent = "Nothing is playing yet.";
        player.classList.add("hidden");
        player.pause();
        player.removeAttribute("src");
        player.load();
        syncPauseResumeLabel();
        return;
    }

    nowPlaying.textContent = `Now Playing: ${currentSong.song} (requested by ${currentSong.requester})`;

    if (currentMediaPath === currentSong.song) {
        return;
    }

    currentMediaPath = currentSong.song;
    const mediaUrl = new URL(`${API_BASE}/rooms/${encodeURIComponent(roomId)}/media`, window.location.origin);
    mediaUrl.searchParams.set("path", currentSong.song);
    mediaUrl.searchParams.set("token", token);

    player.src = mediaUrl.toString();
    player.classList.remove("hidden");
    player.load();
    syncPauseResumeLabel();
    showStatus("Song is ready. Press play to start.");
}

async function startNextSong() {
    const response = await apiRequest(`/rooms/${roomId}/queue/start-next`, {
        method: "POST"
    });

    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Unable to start next song.");
    }

    const room = await response.json();
    renderRoom(room);
    return room;
}

async function finishCurrentSong() {
    const response = await apiRequest(`/rooms/${roomId}/queue/finish-current`, {
        method: "POST"
    });

    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Unable to finish current song.");
    }

    const room = await response.json();
    renderRoom(room);
    return room;
}

async function playOrLoadNext() {
    const player = document.getElementById("karaokePlayer");
    if (player && !player.classList.contains("hidden") && player.src) {
        await player.play();
        syncPauseResumeLabel();
        showStatus("Playing current song.");
        return;
    }

    const room = await startNextSong();
    if (!room.currentSong) {
        showStatus("Queue is empty.");
        return;
    }

    if (player) {
        await player.play();
        syncPauseResumeLabel();
        showStatus("Playing queued song.");
    }
}

async function handleSongEnded() {
    await finishCurrentSong();
    const room = await startNextSong();

    if (room.currentSong) {
        showStatus("Loaded next song. Press Play when ready.");
    } else {
        showStatus("Queue finished.");
    }
}

async function togglePauseResume() {
    const player = document.getElementById("karaokePlayer");
    if (!player || player.classList.contains("hidden") || !player.src) {
        showStatus("No active song loaded yet.");
        return;
    }

    if (player.paused) {
        await player.play();
    } else {
        player.pause();
    }

    syncPauseResumeLabel();
}

function syncPauseResumeLabel() {
    const button = document.getElementById("pauseResumeButton");
    const player = document.getElementById("karaokePlayer");
    if (!button || !player) {
        return;
    }

    const canControlPlayback = !player.classList.contains("hidden") && Boolean(player.src);
    button.disabled = !canControlPlayback;
    button.textContent = canControlPlayback && player.paused ? "Resume" : "Pause";
}

async function openFullscreenPlayer() {
    const shell = document.getElementById("playerVideoShell");
    if (!shell) {
        return;
    }

    try {
        const fullscreenElement = getFullscreenElement();
        if (fullscreenElement) {
            await exitFullscreenMode();
            applyFullscreenSizing(false);
            return;
        }

        await requestFullscreenForElement(shell);
        applyFullscreenSizing(true);
    } catch (error) {
        showStatus("Unable to open fullscreen player.", true);
    }
}

async function handleFullscreenElementChange() {
    const fullscreenElement = getFullscreenElement();
    const player = document.getElementById("karaokePlayer");
    const shell = document.getElementById("playerVideoShell");
    if (!player || !shell || isPromotingFullscreen) {
        return;
    }

    if (!fullscreenElement) {
        applyFullscreenSizing(false);
        return;
    }

    if (fullscreenElement === player) {
        isPromotingFullscreen = true;
        try {
            // Prefer switching directly while already in fullscreen.
            await requestFullscreenForElement(shell);
            showStatus("Fullscreen moved to player shell so QR watermark stays visible.");
        } catch {
            showStatus("Use the Fullscreen button to keep the QR watermark visible.");
        } finally {
            isPromotingFullscreen = false;
        }
        return;
    }

    if (fullscreenElement === shell || fullscreenElement === document.documentElement) {
        applyFullscreenSizing(true);
    }
}

function getFullscreenElement() {
    return document.fullscreenElement || document.webkitFullscreenElement || null;
}

async function requestFullscreenForElement(element) {
    if (element.requestFullscreen) {
        await element.requestFullscreen();
        return;
    }
    if (element.webkitRequestFullscreen) {
        element.webkitRequestFullscreen();
        return;
    }
    throw new Error("Fullscreen API unavailable");
}

function applyFullscreenSizing(isFullscreen) {
    const player = document.getElementById("karaokePlayer");
    if (!player) {
        return;
    }

    if (isFullscreen) {
        player.style.width = "100vw";
        player.style.height = "100vh";
        player.style.maxHeight = "none";
        player.style.marginTop = "0";
        player.style.objectFit = "cover";
        player.style.borderRadius = "0";
        return;
    }

    player.style.removeProperty("width");
    player.style.removeProperty("height");
    player.style.removeProperty("max-height");
    player.style.removeProperty("margin-top");
    player.style.removeProperty("object-fit");
    player.style.removeProperty("border-radius");
}

async function exitFullscreenMode() {
    if (document.fullscreenElement && document.exitFullscreen) {
        await document.exitFullscreen();
        return;
    }
    if (document.webkitFullscreenElement && document.webkitExitFullscreen) {
        document.webkitExitFullscreen();
    }
}

function popOutPlayerWindow() {
    const popoutUrl = new URL(window.location.href);
    popoutUrl.searchParams.set("id", roomId);
    popoutUrl.searchParams.set("popout", "1");
    popoutUrl.searchParams.set("token", token);

    const features = "popup=yes,width=1200,height=780,resizable=yes,scrollbars=yes";
    const child = window.open(popoutUrl.toString(), "karaoke-player-popout", features);
    if (!child) {
        showStatus("Pop-out was blocked by your browser. Allow pop-ups and try again.", true);
    }
}

async function toggleInviteQrWatermark(shouldShow) {
    const watermark = document.getElementById("playerQrWatermark");
    const image = document.getElementById("playerQrWatermarkImage");
    const opacitySlider = document.getElementById("watermarkOpacitySlider");
    if (!watermark || !image) {
        return;
    }

    if (opacitySlider) {
        opacitySlider.disabled = !shouldShow;
    }

    if (!shouldShow) {
        watermark.classList.add("hidden");
        return;
    }

    try {
        if (!inviteQrDataUrl) {
            inviteQrDataUrl = await fetchInviteQrDataUrl();
        }
        image.src = inviteQrDataUrl;
        applyQrWatermarkOpacity();
        watermark.classList.remove("hidden");
    } catch (error) {
        showStatus(error.message || "Unable to load invite QR watermark.", true);
        const toggle = document.getElementById("showInviteQrToggle");
        if (toggle) {
            toggle.checked = false;
        }
        if (opacitySlider) {
            opacitySlider.disabled = true;
        }
    }
}

async function fetchInviteQrDataUrl() {
    const response = await apiRequest(`/rooms/${roomId}/invite`);
    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Unable to generate invite QR code.");
    }

    const payload = await safeJson(response);
    const qrCodeDataUrl = payload.qrCodeDataUrl;
    if (!qrCodeDataUrl) {
        throw new Error("Invite QR code is unavailable for this room.");
    }

    return qrCodeDataUrl;
}

function applyQrWatermarkOpacity() {
    const watermark = document.getElementById("playerQrWatermark");
    const value = document.getElementById("watermarkOpacityValue");
    if (!watermark) {
        return;
    }

    const normalized = Math.max(20, Math.min(90, qrOpacityPercent));
    watermark.style.opacity = String(normalized / 100);
    if (value) {
        value.textContent = `${normalized}%`;
    }
}

function setBackLink(targetRoomId) {
    const link = document.getElementById("backToRoomLink");
    if (!link) {
        return;
    }

    const roomUrl = new URL("/kara-cafe/karaoke-room/room", window.location.origin);
    roomUrl.searchParams.set("id", targetRoomId);
    roomUrl.searchParams.set("token", token);
    link.href = roomUrl.toString();
}

function showStatus(message, isError = false) {
    const status = document.getElementById("playerStatus");
    if (!status) {
        return;
    }

    status.textContent = message;
    status.classList.toggle("warning", Boolean(isError));
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

function escapeHtml(value) {
    return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
