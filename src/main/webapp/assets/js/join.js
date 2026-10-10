const API_BASE = "/kara-cafe/api";

let inviteToken = null;
let roomId = null;

const joinStatus = document.getElementById("joinStatus");
const guestPanelMessage = document.getElementById("guestPanelMessage");
const registerPanel = document.getElementById("registerPanel");
const registerMessage = document.getElementById("registerMessage");

document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(window.location.search);
    inviteToken = params.get("token");
    roomId = params.get("id");

    if (!inviteToken || !roomId) {
        showJoinStatus("Invite link is invalid.", true);
        return;
    }

    localStorage.setItem("karaToken", inviteToken);

    const loginLink = document.getElementById("joinLoginLink");
    if (loginLink) {
        loginLink.href = `/kara-cafe/?next=${encodeURIComponent(`/karaoke-room/room?id=${roomId}`)}`;
    }

    document.getElementById("guestEnterRoomButton")?.addEventListener("click", enterRoomAsGuest);
    document.getElementById("openRegisterButton")?.addEventListener("click", () => {
        registerPanel?.classList.remove("hidden");
    });

    document.getElementById("registerForm")?.addEventListener("submit", registerAccount);

    showJoinStatus("Invite ready. Enter as guest or log in with an account.");
});

function activeInviteToken() {
    return localStorage.getItem("karaToken") || inviteToken;
}

async function enterRoomAsGuest() {
    const nickname = document.getElementById("guestNicknameInput")?.value?.trim();
    if (!nickname) {
        showGuestMessage("Please enter a nickname before joining.", true);
        return;
    }

    let tokenToUse = activeInviteToken();

    if (nickname) {
        try {
            const response = await fetch(`${API_BASE}/auth/guest-nickname`, {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${tokenToUse}`,
                    "Content-Type": "application/x-www-form-urlencoded",
                    Accept: "application/json"
                },
                body: new URLSearchParams({ nickname })
            });

            const payload = await response.json().catch(() => ({}));
            if (!response.ok) {
                throw new Error(payload.error || "Unable to apply nickname.");
            }

            inviteToken = payload.token;
            tokenToUse = inviteToken;
            localStorage.setItem("karaToken", tokenToUse);

            const verified = await verifyGuestSessionUsername(tokenToUse, nickname);
            if (!verified) {
                throw new Error("Nickname was not applied. Please try again.");
            }

            showGuestMessage(`Joining as ${nickname}...`);
        } catch (error) {
            showGuestMessage(error.message, true);
            return;
        }
    }

    const roomUrl = new URL("/kara-cafe/karaoke-room/room", window.location.origin);
    roomUrl.searchParams.set("id", roomId);
    roomUrl.searchParams.set("token", tokenToUse);
    roomUrl.searchParams.set("nickname", nickname);
    window.location.href = roomUrl.toString();
}

async function registerAccount(event) {
    event.preventDefault();

    const form = event.target;
    const username = form.username?.value?.trim();
    const email = form.email?.value?.trim();
    const password = form.password?.value;

    if (!username || !password) {
        showRegisterMessage("Username and password are required.", true);
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/auth/register`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                Accept: "application/json"
            },
            body: new URLSearchParams({ username, email, password })
        });

        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(payload.error || "Unable to create account.");
        }

        localStorage.removeItem("karaToken");
        showRegisterMessage("Account created. Please log in with the new account.");

        const loginUrl = `/kara-cafe/?next=${encodeURIComponent(`/karaoke-room/room?id=${roomId}`)}`;
        setTimeout(() => {
            window.location.replace(loginUrl);
        }, 1200);
    } catch (error) {
        showRegisterMessage(error.message, true);
    }
}

async function verifyGuestSessionUsername(sessionToken, expectedNickname) {
    const response = await fetch(`${API_BASE}/auth/session`, {
        headers: {
            Authorization: `Bearer ${sessionToken}`,
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        return false;
    }

    const payload = await response.json().catch(() => ({}));
    return (payload.username || "").toLowerCase() === expectedNickname.toLowerCase();
}

function showJoinStatus(message, isError = false) {
    if (!joinStatus) {
        return;
    }
    joinStatus.textContent = message;
    joinStatus.classList.toggle("warning", Boolean(isError));
}

function showGuestMessage(message, isError = false) {
    if (!guestPanelMessage) {
        return;
    }
    guestPanelMessage.textContent = message;
    guestPanelMessage.classList.toggle("warning", Boolean(isError));
}

function showRegisterMessage(message, isError = false) {
    if (!registerMessage) {
        return;
    }
    registerMessage.textContent = message;
    registerMessage.classList.toggle("warning", Boolean(isError));
}