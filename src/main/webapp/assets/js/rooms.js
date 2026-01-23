const API_BASE = "/kara-cafe/api";
const adminRoles = new Set(["ADMIN", "SUPER_ADMIN"]);

const roomList = document.getElementById("roomList");
const status = document.getElementById("status");
const createForm = document.getElementById("createRoomForm");
const roomNameInput = document.getElementById("roomNameInput");
const createMessage = document.getElementById("createMessage");

const adminPanel = document.getElementById("adminPanel");
const createAdminForm = document.getElementById("createAdminForm");
const adminMessage = document.getElementById("adminMessage");

let token = null;
let currentUser = null;

document.addEventListener("DOMContentLoaded", () => {
    token = localStorage.getItem("karaToken");
    if (!token) {
        redirectToLogin();
        return;
    }

    document.getElementById("logoutButton")?.addEventListener("click", async () => {
        await logout();
    });

    createForm?.addEventListener("submit", handleCreateRoom);
    createAdminForm?.addEventListener("submit", handleCreateAdmin);

    roomList?.addEventListener("click", handleRoomClick);
    roomList?.addEventListener("submit", handleInviteForm);

    loadRooms().catch((error) => {
        if (error.message !== "Authentication required") {
            showStatus(error.message, true);
        }
    });
});

async function logout() {
    try {
        await fetch(`${API_BASE}/auth/logout`, {
            method: "DELETE",
            headers: {
                Authorization: `Bearer ${token}`,
                Accept: "application/json"
            }
        });
    } catch (error) {
        console.error("Logout failed", error);
    } finally {
        redirectToLogin();
    }
}

function redirectToLogin() {
    localStorage.removeItem("karaToken");
    token = null;
    window.location.replace("/kara-cafe/");
}

async function loadRooms() {
    const data = await fetchRooms();
    if (!data?.currentUser) {
        throw new Error("Unable to retrieve user.");
    }

    currentUser = data.currentUser;
    currentUser.isAdmin = adminRoles.has(currentUser.role);
    currentUser.isSuperAdmin = currentUser.role === "SUPER_ADMIN";

    if (createForm) {
        createForm.classList.toggle("hidden", !currentUser.isAdmin);
    }

    if (adminPanel) {
        adminPanel.classList.toggle("hidden", !currentUser.isSuperAdmin);
        if (!currentUser.isSuperAdmin) {
            createAdminForm?.classList.add("hidden");
        }
    }

    renderRooms(data.rooms || []);
}

async function fetchRooms() {
    const response = await fetch(`${API_BASE}/rooms/list`, {
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/json"
        }
    });

    ensureAuth(response);

    if (!response.ok) {
        throw new Error("Unable to load rooms.");
    }

    return response.json();
}

async function handleCreateRoom(event) {
    event.preventDefault();
    if (!currentUser?.isAdmin) {
        displayCreateMessage("Admins only.", true);
        return;
    }

    const roomName = roomNameInput?.value.trim();
    if (!roomName) {
        displayCreateMessage("Room name is required.", true);
        return;
    }

    displayCreateMessage("Creating room…");

    try {
        const response = await fetch(`${API_BASE}/rooms/create`, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams({ roomName })
        });

        ensureAuth(response);

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.error || "Unable to create room.");
        }

        displayCreateMessage("Room created.");
        roomNameInput.value = "";
        await loadRooms();
    } catch (error) {
        displayCreateMessage(error.message, true);
    }
}

async function handleCreateAdmin(event) {
    event.preventDefault();
    if (!currentUser?.isSuperAdmin) {
        displayAdminMessage("Only Super Admins can create admin users.", true);
        return;
    }

    const formData = new URLSearchParams(new FormData(event.target));

    displayAdminMessage("Creating admin…");

    try {
        const response = await fetch(`${API_BASE}/users/admin`, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: formData
        });

        ensureAuth(response);

        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            displayAdminMessage(payload.error || "Unable to create admin.", true);
            return;
        }

        displayAdminMessage(`Admin ${payload.username} created.`);
        event.target.reset();
    } catch (error) {
        displayAdminMessage(error.message, true);
    }
}

function renderRooms(rooms) {
    if (!rooms.length) {
        roomList.innerHTML = "<p class=\"subtext\">No rooms yet. Create one to get started.</p>";
        showStatus("No rooms available.");
        return;
    }

    showStatus(`${rooms.length} room(s) available.`);
    roomList.innerHTML = rooms.map(renderRoomCard).join("");
}

function renderRoomCard(room) {
    const canManage =
        currentUser &&
        (currentUser.isSuperAdmin || room.owner === currentUser.username);

    const inviteList = room.songRequests || [];
    const inviteHtml = inviteList.length
        ? inviteList
            .map(
                (invite) =>
                    `<li>${escapeHtml(invite.song)} <span class="subtext">(${escapeHtml(invite.requester)})</span></li>`
            )
            .join("")
        : "<li class=\"subtext\">No song requests yet.</li>";

    const inviteForm =
        currentUser?.isAdmin && canManage
            ? `<form class="invite-form" data-room-id="${room.id}">
           <input name="guest" placeholder="Invite username" autocomplete="off" />
           <button class="tiny-button" type="submit">Invite</button>
         </form>`
            : "";

    return `
    <article class="room-item" data-room-id="${room.id}">
      <header class="room-header">
        <div>
          <h2>${escapeHtml(room.name)}</h2>
          <p class="subtext">Owner: ${escapeHtml(room.owner)}</p>
        </div>
        <div class="room-actions">
          <button class="button enter-room" data-room-id="${room.id}">Enter room</button>
          ${canManage ? `<button class="button delete-room" data-room-id="${room.id}">Delete</button>` : ""}
        </div>
      </header>
      <div class="room-meta">
        <p><strong>Occupants:</strong> ${room.occupants}</p>
        <p><strong>Uptime:</strong> ${escapeHtml(room.uptime)}</p>
      </div>
      ${inviteForm}
      <div class="invite-section">
        <p class="subtext"><strong>Song requests</strong></p>
        <ul class="invite-list">
          ${inviteHtml}
        </ul>
      </div>
    </article>`;
}

async function handleRoomClick(event) {
    const target = event.target;

    if (target.matches(".enter-room")) {
        const roomId = target.dataset.roomId;
        if (roomId) {
            window.location.href = `karaoke-room/room.html?id=${roomId}`;
        }
        return;
    }

    if (target.matches(".delete-room")) {
        const roomId = target.dataset.roomId;
        if (!roomId) {
            return;
        }

        if (!confirm("Are you sure you want to delete this room?")) {
            return;
        }

        try {
            const response = await fetch(`${API_BASE}/rooms/${roomId}`, {
                method: "DELETE",
                headers: {
                    Authorization: `Bearer ${token}`,
                    Accept: "application/json"
                }
            });

            ensureAuth(response);

            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                throw new Error(data.error || "Unable to delete room.");
            }

            showStatus("Room deleted.");
            await loadRooms();
        } catch (error) {
            showStatus(error.message, true);
        }
        return;
    }
}

async function handleInviteForm(event) {
    if (!event.target.matches(".invite-form")) {
        return;
    }
    event.preventDefault();
    const form = event.target;
    const guest = form.guest?.value.trim();
    const roomId = form.dataset.roomId;

    if (!guest || !roomId) {
        showStatus("Guest username is required.", true);
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/rooms/${roomId}/invite`, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/x-www-form-urlencoded",
                Accept: "application/json"
            },
            body: new URLSearchParams({ guest })
        });

        ensureAuth(response);

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.error || "Unable to invite");
        }

        const payload = await response.json();
        const inviteLink = `${window.location.origin}${payload.entryUrl}`;

        showStatus(`Invited ${guest}. Share this link: ${inviteLink}`);
        form.reset();
        await loadRooms();
    } catch (error) {
        showStatus(error.message, true);
    }
}

function showStatus(message, isError = false) {
    if (!status) {
        return;
    }
    status.textContent = message;
    status.classList.toggle("warning", Boolean(isError));
}

function displayCreateMessage(message, isError = false) {
    if (!createMessage) {
        return;
    }
    createMessage.textContent = message;
    createMessage.classList.toggle("warning", Boolean(isError));
}

function displayAdminMessage(message, isError = false) {
    if (!adminMessage) {
        return;
    }
    adminMessage.textContent = message;
    adminMessage.classList.toggle("warning", Boolean(isError));
}

function ensureAuth(response) {
    if (response.status === 401 || response.status === 403) {
        redirectToLogin();
        throw new Error("Authentication required");
    }
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}
