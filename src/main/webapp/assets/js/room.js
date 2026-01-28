document.addEventListener("DOMContentLoaded", () => {
    console.log("Room page load initiated");
    const tokenFromQuery = new URLSearchParams(window.location.search).get("token");
    if (tokenFromQuery) {
        console.log("Token captured from query");
        localStorage.setItem("karaToken", tokenFromQuery);
    }

    const token = localStorage.getItem("karaToken");
    const roomId = new URLSearchParams(window.location.search).get("id");

    if (!token || !roomId) {
        console.warn("Missing token or room id for room page");
        showStatus("Missing room ID or token.", true);
        return;
    }

    setSearchLink(roomId, token);

    enterRoom(roomId, token)
        .then((room) => {
            console.log("Room data received", room);
            renderRoom(room);
        })
        .catch((error) => {
            console.error("Failed to enter room", error);
            showStatus(error.message, true);
        });
});

async function enterRoom(roomId, token) {
    console.log(`Entering room ${roomId}`);
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

function renderRoom(room) {
    console.log("Rendering room state", room);
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
        : '<li class="subtext">No song requests yet.</li>';
}

function setSearchLink(roomId, token) {
    const link = document.getElementById("searchVideosLink");
    if (link) {
        const searchUrl = new URL("/kara-cafe/karaoke-room/search", window.location.origin);
        searchUrl.searchParams.set("id", roomId);
        searchUrl.searchParams.set("token", token);
        link.href = searchUrl.toString();
        console.log("Search link updated", link.href);
    }
}

function showStatus(message, isError = false) {
    const status = document.getElementById("roomStatus");
    status.textContent = message;
    status.classList.toggle("warning", Boolean(isError));
    console.log("Status message shown", { message, isError });
}

function escapeHtml(value) {
    return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
