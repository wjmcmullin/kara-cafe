document.addEventListener("DOMContentLoaded", () => {
    const tokenFromQuery = new URLSearchParams(window.location.search).get("token");
    if (tokenFromQuery) {
        localStorage.setItem("karaToken", tokenFromQuery);
    }

    const token = localStorage.getItem("karaToken");
    const roomId = new URLSearchParams(window.location.search).get("id");

    if (!token || !roomId) {
        showStatus("Missing room ID or token.", true);
        return;
    }

    enterRoom(roomId, token)
        .then(renderRoom)
        .catch((error) => showStatus(error.message, true));

    document.getElementById("songForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const song = event.target.song.value.trim();
        if (!song) {
            showStatus("Enter a song name.", true);
            return;
        }

        try {
            const room = await requestSong(roomId, song, token);
            renderRoom(room);
            event.target.reset();
            showStatus(`Requested "${song}".`);
        } catch (error) {
            showStatus(error.message, true);
        }
    });
});

async function enterRoom(roomId, token) {
    const response = await fetch(`/kara-cafe/api/rooms/${roomId}/enter`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        throw new Error("Unable to enter room.");
    }

    return response.json();
}

async function requestSong(roomId, song, token) {
    const response = await fetch(`/kara-cafe/api/rooms/${roomId}/song-request`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams({ song })
    });

    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data.error || "Song request failed.");
    }

    return response.json();
}

function renderRoom(room) {
    document.getElementById("roomName").textContent = room.name;
    document.getElementById("roomOwner").textContent = `Owner: ${room.owner}`;
    document.getElementById("roomOccupants").textContent = room.occupants;
    document.getElementById("roomUptime").textContent = room.uptime;

    const songs = room.songRequests || [];
    const songList = document.getElementById("songList");
    songList.innerHTML = songs.length
        ? songs
            .map(
                (entry) =>
                    `<li>${escapeHtml(entry.song)} <span class="subtext">(${escapeHtml(entry.requester)})</span></li>`
            )
            .join("")
        : "<li class=\"subtext\">No song requests yet.</li>";
}

function showStatus(message, isError = false) {
    const status = document.getElementById("roomStatus");
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
