const API_BASE = "/kara-cafe/api";

let token = null;
let roomId = null;
let mediaDirectory = null;
let roomPollTimer = null;
let sessionInfo = null;

document.addEventListener("DOMContentLoaded", async () => {
    const params = new URLSearchParams(window.location.search);
    const tokenFromQuery = params.get("token");
    const nicknameFromQuery = params.get("nickname");
    roomId = params.get("id");

    if (tokenFromQuery) {
        localStorage.setItem("karaToken", tokenFromQuery);
        params.delete("token");
    }

    if (nicknameFromQuery) {
        params.delete("nickname");
    }

    if (tokenFromQuery || nicknameFromQuery) {
        const sanitizedQuery = params.toString();
        const cleanUrl = `${window.location.pathname}${sanitizedQuery ? `?${sanitizedQuery}` : ""}`;
        window.history.replaceState({}, "", cleanUrl);
    }

    token = localStorage.getItem("karaToken");

    if (!token || !roomId) {
        showStatus("Missing room ID or token.", true);
        return;
    }

    bindSongActions();

    try {
        sessionInfo = await loadSession();

        if (nicknameFromQuery && sessionInfo?.inviteOnly) {
            const currentName = (sessionInfo.username || "").toLowerCase();
            const requestedName = nicknameFromQuery.toLowerCase();
            if (currentName !== requestedName) {
                await ensureGuestNickname(nicknameFromQuery);
                sessionInfo = await loadSession();
            }
        }

        applyRoleVisibility();
        setPlayerLink(roomId);
        await enterRoom(roomId);
        await loadConfiguredDirectoryAndSongs();
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

function bindSongActions() {
    document.getElementById("videoResults")?.addEventListener("click", async (event) => {
        const button = event.target.closest("button.request-video");
        if (!button) {
            return;
        }

        const path = button.dataset.path;
        if (!path) {
            return;
        }

        try {
            await requestSong(path);
            await refreshRoom();
            showStatus(`Added to queue: ${path}`);
        } catch (error) {
            showStatus(error.message, true);
        }
    });
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

async function ensureGuestNickname(nickname) {
    const response = await fetch(`${API_BASE}/auth/guest-nickname`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/x-www-form-urlencoded",
            Accept: "application/json"
        },
        body: new URLSearchParams({ nickname: nickname.trim() })
    });

    if (response.status === 401) {
        localStorage.removeItem("karaToken");
        window.location.replace("/kara-cafe/");
        throw new Error("Authentication required");
    }

    const payload = await safeJson(response);
    if (!response.ok) {
        throw new Error(payload.error || "Unable to apply nickname.");
    }

    token = payload.token;
    localStorage.setItem("karaToken", token);
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
        throw new Error(data.error || "Unable to load room.");
    }

    const room = await response.json();
    renderRoom(room);
}

function renderRoom(room) {
    document.getElementById("roomName").textContent = room.name;
    document.getElementById("roomOwner").textContent = `Owner: ${room.owner}`;
    document.getElementById("roomOccupants").textContent = room.occupants;
    document.getElementById("roomUptime").textContent = room.uptime;

    const nowPlaying = document.getElementById("nowPlaying");
    if (nowPlaying) {
        nowPlaying.textContent = room.currentSong
            ? `${room.currentSong.song} (requested by ${room.currentSong.requester})`
            : "Nothing is playing yet.";
    }

    const queue = room.songRequests || [];
    const songList = document.getElementById("songList");
    songList.innerHTML = "";

    if (!queue.length) {
        const li = document.createElement("li");
        li.className = "subtext";
        li.textContent = "Queue is empty.";
        songList.appendChild(li);
        return;
    }

    const fragment = document.createDocumentFragment();
    queue.forEach((entry, index) => {
        const li = document.createElement("li");
        li.innerHTML = `${index + 1}. ${escapeHtml(entry.song)} <span class="subtext">(${escapeHtml(entry.requester)})</span>`;
        fragment.appendChild(li);
    });
    songList.appendChild(fragment);
}

async function loadConfiguredDirectoryAndSongs() {
    const response = await apiRequest(`/rooms/config/video-dir`);
    if (!response.ok) {
        throw new Error("Unable to load video directory.");
    }

    const payload = await response.json();
    mediaDirectory = payload?.directory;

    const directoryLabel = document.getElementById("configuredDirectory");
    if (directoryLabel) {
        directoryLabel.textContent = mediaDirectory || "(default)";
    }

    await loadAvailableSongs();
    showStatus("Songs loaded.");
}

async function loadAvailableSongs() {
    const query = mediaDirectory ? `?dir=${encodeURIComponent(mediaDirectory)}` : "";
    const response = await apiRequest(`/rooms/${roomId}/search${query}`);
    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Unable to list karaoke videos.");
    }

    const payload = await response.json();
    const entries = payload.entries || [];
    const list = document.getElementById("videoResults");

    list.innerHTML = "";
    if (!entries.length) {
        const li = document.createElement("li");
        li.className = "subtext";
        li.textContent = "No karaoke videos found.";
        list.appendChild(li);
        return;
    }

    const fragment = document.createDocumentFragment();
    entries.forEach((entry) => {
        const li = document.createElement("li");
        const container = document.createElement("div");
        container.className = "video-entry";

        const name = document.createElement("span");
        name.textContent = entry.name || entry.relativePath || "(unnamed)";

        const button = document.createElement("button");
        button.className = "tiny-button request-video";
        button.type = "button";
        button.textContent = "Add to queue";
        button.dataset.path = entry.relativePath || "";

        container.appendChild(name);
        container.appendChild(button);
        li.appendChild(container);
        fragment.appendChild(li);
    });

    list.appendChild(fragment);
}

async function requestSong(songPath) {
    const response = await apiRequest(`/rooms/${roomId}/song-request`, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams({ song: songPath })
    });

    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Song request failed.");
    }

    return response.json();
}

function setPlayerLink(targetRoomId) {
    const link = document.getElementById("openPlayerLink");
    if (!link) {
        return;
    }

    const role = sessionInfo?.role;
    const canOpenPlayer = role === "ADMIN" || role === "SUPER_ADMIN";
    if (!canOpenPlayer) {
        link.classList.add("hidden");
        return;
    }

    const playerUrl = new URL("/kara-cafe/karaoke-room/player", window.location.origin);
    playerUrl.searchParams.set("id", targetRoomId);
    playerUrl.searchParams.set("token", token);
    link.href = playerUrl.toString();
    link.classList.remove("hidden");
}

function applyRoleVisibility() {
    const backLink = document.getElementById("backToRoomsLink");
    if (!backLink) {
        return;
    }

    if (sessionInfo?.inviteOnly) {
        backLink.classList.add("hidden");
        return;
    }

    backLink.classList.remove("hidden");
}

function showStatus(message, isError = false) {
    const status = document.getElementById("roomStatus");
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