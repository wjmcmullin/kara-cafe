const API_BASE = "/kara-cafe/api";
const DEFAULT_SEARCH_DIR = "/karaoke-videos";

let currentToken = null;
let currentRoomId = null;

document.addEventListener("DOMContentLoaded", () => {
    console.log("Search page loading");
    const params = new URLSearchParams(window.location.search);
    currentToken = params.get("token") || localStorage.getItem("karaToken");
    currentRoomId = params.get("id");

    if (!currentToken || !currentRoomId) {
        console.warn("Missing token or room id for search page");
        showStatus("Missing room ID or token.", true);
        return;
    }

    localStorage.setItem("karaToken", currentToken);

    const backLink = document.getElementById("backToRoomLink");
    if (backLink) {
        const url = new URL("/kara-cafe/karaoke-room/room", window.location.origin);
        url.searchParams.set("id", currentRoomId);
        url.searchParams.set("token", currentToken);
        backLink.href = url.toString();
    }

    document.getElementById("videoResults")?.addEventListener("click", handleRequestClick);

    document.getElementById("searchLogoutButton")?.addEventListener("click", async () => {
        console.log("Search page logout invoked");
        try {
            await fetch(`${API_BASE}/auth/logout`, {
                method: "DELETE",
                headers: {
                    Authorization: `Bearer ${currentToken}`,
                    Accept: "application/json"
                }
            });
        } catch (error) {
            console.error("Logout failed", error);
        } finally {
            localStorage.removeItem("karaToken");
            window.location.replace("/kara-cafe/");
        }
    });

    loadConfiguredDirectoryAndSearch();
});

async function loadConfiguredDirectoryAndSearch() {
    console.log("Requesting configured media directory");
    const status = document.getElementById("configuredDirectory");
    let directory = DEFAULT_SEARCH_DIR;

    try {
        const response = await fetch(`${API_BASE}/rooms/config/video-dir`, {
            headers: {
                Authorization: `Bearer ${currentToken}`,
                Accept: "application/json"
            }
        });

        ensureAuth(response);

        if (!response.ok) {
            throw new Error("Unable to load video directory.");
        }

        const payload = await response.json();
        directory = payload?.directory || DEFAULT_SEARCH_DIR;
        console.log("Configured directory received", directory);
    } catch (error) {
        console.error("Failed to load directory", error);
        showStatus(error.message, true);
    }

    if (status) {
        status.textContent = directory;
    }

    performSearch(directory);
}

async function performSearch(directory) {
    console.log("Searching videos in directory", directory);
    showStatus("Searching…");

    try {
        const response = await fetch(`${API_BASE}/rooms/${currentRoomId}/search?dir=${encodeURIComponent(directory)}`, {
            headers: {
                Authorization: `Bearer ${currentToken}`,
                Accept: "application/json"
            }
        });

        ensureAuth(response);

        if (!response.ok) {
            const data = await safeJson(response);
            throw new Error(data.error || "Unable to list karaoke videos.");
        }

        const videos = await response.json();
        renderResults(directory, videos.entries || []);
    } catch (error) {
        console.error("Search failed", error);
        showStatus(error.message, true);
    }
}

function renderResults(directory, videos) {
    console.log("Rendering search results", { directory, count: videos.length });
    showStatus(`Showing ${videos.length} file(s) in ${directory}.`);
    const list = document.getElementById("videoResults");
    if (!list) {
        return;
    }

    if (!videos.length) {
        list.innerHTML = '<li class="subtext">No karaoke videos found.</li>';
        return;
    }

    list.innerHTML = videos
        .map((entry) => {
            const display = escapeHtml(entry.name);
            return `
                <li>
                    <div class="video-entry">
                        <span>${display}</span>
                        <button class="tiny-button request-video" type="button" data-path="${escapeHtml(
                entry.relativePath
            )}">Add to queue</button>
                    </div>
                </li>`;
        })
        .join("");
}

async function handleRequestClick(event) {
    const button = event.target.closest("button.request-video");
    if (!button) {
        return;
    }

    const path = button.dataset.path;
    if (!path) {
        return;
    }

    console.log("Requesting song", path);
    try {
        await requestSong(currentRoomId, path);
        showStatus(`Queued “${path}”.`);
    } catch (error) {
        console.error("Failed to request song", error);
        showStatus(error.message, true);
    }
}

async function requestSong(roomId, song) {
    const response = await fetch(`${API_BASE}/rooms/${roomId}/song-request`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${currentToken}`,
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

function showStatus(message, isError = false) {
    const status = document.getElementById("searchStatus");
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

function ensureAuth(response) {
    if (response.status === 401 || response.status === 403) {
        window.location.replace("/kara-cafe/");
        throw new Error("Authentication required");
    }
}
