const API_BASE = "/kara-cafe/api";
const adminRoles = new Set(["ADMIN", "SUPER_ADMIN"]);
const ROOM_TEMPLATE_URL = "/kara-cafe/karaoke-room/rooms.html";

const roomList = document.getElementById("roomList");
const status = document.getElementById("status");
const createForm = document.getElementById("createRoomForm");
const roomNameInput = document.getElementById("roomNameInput");
const createMessage = document.getElementById("createMessage");

const adminPanel = document.getElementById("adminPanel");
const createAdminForm = document.getElementById("createAdminForm");
const adminMessage = document.getElementById("adminMessage");

const superAdminListing = document.getElementById("superAdminListing");
const superAdminList = document.getElementById("superAdminList");
const superAdminMessage = document.getElementById("superAdminMessage");

let token = null;
let currentUser = null;
let roomCardTemplate = null;

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
    superAdminList?.addEventListener("click", handleSuperAdminActions);

    roomList?.addEventListener("click", handleRoomClick);
    roomList?.addEventListener("submit", handleInviteForm);

    initializePage();
});

async function initializePage() {
    try {
        await loadRoomTemplate();
        await loadRooms();
    } catch (error) {
        if (error.message !== "Authentication required") {
            showStatus(error.message, true);
        }
    }
}

async function loadRoomTemplate() {
    if (roomCardTemplate) {
        return;
    }

    const response = await fetch(ROOM_TEMPLATE_URL);
    if (!response.ok) {
        throw new Error("Unable to load room template.");
    }

    const text = await response.text();
    const parser = new DOMParser();
    const doc = parser.parseFromString(text, "text/html");
    const template = doc.querySelector("#roomCardTemplate");
    if (!template) {
        throw new Error("Room template is missing.");
    }

    roomCardTemplate = template;
}

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

    console.debug("loadRooms current user:", currentUser);

    if (createForm) {
        createForm.classList.toggle("hidden", !currentUser.isAdmin);
    }

    if (adminPanel) {
        adminPanel.classList.toggle("hidden", !currentUser.isSuperAdmin);
        if (!currentUser.isSuperAdmin) {
            createAdminForm?.classList.add("hidden");
        }
    }

    if (superAdminListing) {
        if (currentUser.isSuperAdmin) {
            superAdminListing.classList.remove("hidden");
            await loadAdmins();
        } else {
            superAdminListing.classList.add("hidden");
        }
    }

    updateProfileLink();
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

function ensureProfileLink() {
    let profileLink = document.getElementById("profileLink");
    if (profileLink) {
        return profileLink;
    }

    const nav = document.querySelector(".room-nav");
    if (!nav) {
        console.warn("room-nav not found, cannot insert profile link.");
        return null;
    }

    profileLink = document.createElement("a");
    profileLink.id = "profileLink";
    profileLink.className = "link hidden";
    profileLink.href = "#";
    profileLink.textContent = "My profile";
    nav.insertBefore(profileLink, nav.firstElementChild);
    return profileLink;
}

function updateProfileLink() {
    const profileLink = ensureProfileLink();
    if (!profileLink) {
        console.warn("Profile link anchor missing from DOM");
        return;
    }

    profileLink.classList.add("hidden");

    if (currentUser?.isSuperAdmin) {
        profileLink.textContent = "Super Admin profile";
        profileLink.href = "/kara-cafe/karaoke-room/super-admin-profile.html";
        profileLink.classList.remove("hidden");
    } else if (currentUser?.isAdmin) {
        profileLink.textContent = "Admin profile";
        profileLink.href = "/kara-cafe/karaoke-room/admin-profile.html";
        profileLink.classList.remove("hidden");
    }

    console.debug(
        "updateProfileLink role:",
        currentUser?.role,
        "visible:",
        !profileLink.classList.contains("hidden")
    );
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
        await loadAdmins();
    } catch (error) {
        displayAdminMessage(error.message, true);
    }
}

async function loadAdmins() {
    if (!superAdminList) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/admin/users`, {
            headers: {
                Authorization: `Bearer ${token}`,
                Accept: "application/json"
            }
        });

        ensureAuth(response);

        if (!response.ok) {
            const payload = await response.json().catch(() => ({}));
            throw new Error(payload.error || "Unable to load admin users.");
        }

        const admins = await response.json();
        const filteredAdmins = Array.isArray(admins)
            ? admins.filter((admin) => admin.role !== "SUPER_ADMIN")
            : [];
        renderAdminList(filteredAdmins);
    } catch (error) {
        showSuperAdminMessage(error.message, true);
    }
}

function renderAdminList(admins) {
    if (!superAdminList) {
        return;
    }

    if (!admins.length) {
        superAdminList.innerHTML = '<p class="subtext">No admins created yet.</p>';
        showSuperAdminMessage("Create your first admin above.");
        return;
    }

    superAdminList.innerHTML = admins
        .map(
            (admin) => `
        <div class="admin-entry" data-admin="${escapeHtml(admin.username)}">
          <span class="admin-name">${escapeHtml(admin.username)}</span>
          <div class="admin-actions">
            <form class="inline-form change-password-form" data-admin="${escapeHtml(admin.username)}">
              <input name="password" type="password" placeholder="New password" autocomplete="new-password" required />
              <button class="tiny-button" type="submit">Change password</button>
            </form>
            <button class="button delete-admin" type="button" data-admin="${escapeHtml(admin.username)}">Delete</button>
          </div>
        </div>`
        )
        .join("");
    showSuperAdminMessage(`${admins.length} admin(s) found.`);
}

async function handleSuperAdminActions(event) {
    const target = event.target;

    if (target.matches(".delete-admin")) {
        const username = target.dataset.admin;
        if (!username) {
            return;
        }

        if (!confirm(`Delete admin ${username}? This cannot be undone.`)) {
            return;
        }

        await deleteAdmin(username);
        return;
    }

    const form = target.closest(".change-password-form");
    if (form && form.dataset.admin) {
        event.preventDefault();
        const username = form.dataset.admin;
        const password = form.password?.value?.trim();
        if (!password) {
            showSuperAdminMessage("Password is required to update.", true);
            return;
        }
        await changeAdminPassword(username, password);
    }
}

async function changeAdminPassword(username, password) {
    try {
        const response = await fetch(`${API_BASE}/admin/users/${encodeURIComponent(username)}/password`, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams({ password })
        });

        ensureAuth(response);

        if (!response.ok) {
            const payload = await response.json().catch(() => ({}));
            throw new Error(payload.error || "Unable to change password.");
        }

        showSuperAdminMessage(`Password updated for ${username}.`);
        await loadAdmins();
    } catch (error) {
        showSuperAdminMessage(error.message, true);
    }
}

async function deleteAdmin(username) {
    try {
        const response = await fetch(`${API_BASE}/admin/users/${encodeURIComponent(username)}`, {
            method: "DELETE",
            headers: {
                Authorization: `Bearer ${token}`,
                Accept: "application/json"
            }
        });

        ensureAuth(response);

        if (!response.ok) {
            const payload = await response.json().catch(() => ({}));
            throw new Error(payload.error || "Unable to delete admin.");
        }

        showSuperAdminMessage(`Admin ${username} deleted.`);
        await loadAdmins();
    } catch (error) {
        showSuperAdminMessage(error.message, true);
    }
}

function renderRooms(rooms) {
    if (!roomList) {
        return;
    }

    if (!roomCardTemplate) {
        showStatus("Room template unavailable.", true);
        return;
    }

    if (!rooms.length) {
        roomList.innerHTML = '<p class="subtext">No rooms yet. Create one to get started.</p>';
        showStatus("No rooms available.");
        return;
    }

    showStatus(`${rooms.length} room(s) available.`);
    roomList.innerHTML = "";
    const fragment = document.createDocumentFragment();

    rooms.forEach((room) => {
        const element = createRoomElement(room);
        if (element) {
            fragment.appendChild(element);
        }
    });

    roomList.appendChild(fragment);
}

function createRoomElement(room) {
    const canManage =
        currentUser &&
        (currentUser.isSuperAdmin || room.owner === currentUser.username);

    const inviteList = room.songRequests || [];
    const clone = roomCardTemplate.content.firstElementChild.cloneNode(true);
    clone.dataset.roomId = room.id;

    const nameEl = clone.querySelector("[data-room-name]");
    const ownerEl = clone.querySelector("[data-room-owner]");
    const occupantsEl = clone.querySelector("[data-room-occupants]");
    const uptimeEl = clone.querySelector("[data-room-uptime]");
    const enterButton = clone.querySelector(".enter-room");
    const deleteButton = clone.querySelector(".delete-room");
    const inviteForm = clone.querySelector(".invite-form");
    const inviteListEl = clone.querySelector("[data-invite-list]");

    if (nameEl) {
        nameEl.textContent = escapeHtml(room.name);
    }
    if (ownerEl) {
        ownerEl.textContent = escapeHtml(room.owner);
    }
    if (occupantsEl) {
        occupantsEl.textContent = room.occupants;
    }
    if (uptimeEl) {
        uptimeEl.textContent = escapeHtml(room.uptime);
    }
    if (enterButton) {
        enterButton.dataset.roomId = room.id;
    }
    if (deleteButton) {
        deleteButton.dataset.roomId = room.id;
        deleteButton.classList.toggle("hidden", !canManage);
    }
    if (inviteForm) {
        inviteForm.dataset.roomId = room.id;
        inviteForm.classList.toggle("hidden", !(currentUser?.isAdmin && canManage));
    }

    if (inviteListEl) {
        inviteListEl.innerHTML = "";
        if (inviteList.length) {
            inviteList.forEach((invite) => {
                const li = document.createElement("li");
                li.innerHTML = `${escapeHtml(invite.song)} <span class="subtext">(${escapeHtml(
                    invite.requester
                )})</span>`;
                inviteListEl.appendChild(li);
            });
        } else {
            const li = document.createElement("li");
            li.classList.add("subtext");
            li.textContent = "No song requests yet.";
            inviteListEl.appendChild(li);
        }
    }

    return clone;
}

async function handleRoomClick(event) {
    const target = event.target;

    if (target.matches(".enter-room")) {
        const roomId = target.dataset.roomId;
        if (roomId) {
            window.location.href = `/kara-cafe/karaoke-room/room?id=${roomId}`;
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

function showSuperAdminMessage(message, isError = false) {
    if (!superAdminMessage) {
        return;
    }
    superAdminMessage.textContent = message;
    superAdminMessage.classList.toggle("warning", Boolean(isError));
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
