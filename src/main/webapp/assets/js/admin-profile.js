const API_BASE = "/kara-cafe/api";
const PROFILE_ENDPOINT = `${API_BASE}/users/profile`;
const PASSWORD_ENDPOINT = `${PROFILE_ENDPOINT}/password`;
const USERNAME_ENDPOINT = `${PROFILE_ENDPOINT}/username`;
const EMAIL_ENDPOINT = `${PROFILE_ENDPOINT}/email`;

let token = null;
const usernameForm = document.getElementById("usernameForm");
const usernameInput = document.getElementById("usernameInput");
const usernameMessage = document.getElementById("usernameMessage");

const emailForm = document.getElementById("emailForm");
const emailInput = document.getElementById("emailInput");
const emailMessage = document.getElementById("emailMessage");

document.addEventListener("DOMContentLoaded", () => {
    token = localStorage.getItem("karaToken");
    if (!token) {
        redirectToLogin();
        return;
    }

    document.getElementById("logoutButton")?.addEventListener("click", async () => {
        await logout();
    });

    document.getElementById("passwordForm")?.addEventListener("submit", handlePasswordUpdate);
    usernameForm?.addEventListener("submit", handleUsernameUpdate);
    emailForm?.addEventListener("submit", handleEmailUpdate);
    loadProfile();
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
    window.location.replace("/kara-cafe/");
}

async function loadProfile() {
    const response = await fetch(PROFILE_ENDPOINT, {
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/json"
        }
    });

    ensureAuth(response);

    if (!response.ok) {
        setMessage("Unable to load profile.", true);
        return;
    }

    const profile = await response.json();
    if (profile.role !== "ADMIN") {
        redirectToLogin();
        return;
    }

    document.getElementById("profileUsername").textContent = profile.username;
    document.getElementById("profileRole").textContent = "ADMIN";
    document.getElementById("profileEmail").textContent = profile.email || "—";
    if (usernameInput) {
        usernameInput.value = profile.username;
    }
    if (emailInput) {
        emailInput.value = profile.email || "";
    }
}

async function handlePasswordUpdate(event) {
    event.preventDefault();
    const form = event.target;
    const password = form.password?.value?.trim();
    const confirm = form.confirmPassword?.value?.trim();

    if (!password) {
        setMessage("Password is required.", true);
        return;
    }

    if (password !== confirm) {
        setMessage("Passwords do not match.", true);
        return;
    }

    try {
        const response = await fetch(PASSWORD_ENDPOINT, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/x-www-form-urlencoded",
                Accept: "application/json"
            },
            body: new URLSearchParams({ password })
        });

        ensureAuth(response);

        if (!response.ok) {
            const payload = await response.json().catch(() => ({}));
            throw new Error(payload.error || "Unable to update password.");
        }

        setMessage("Password updated successfully.");
        form.reset();
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function handleUsernameUpdate(event) {
    event.preventDefault();
    const newUsername = usernameInput?.value?.trim();

    if (!newUsername) {
        setUsernameMessage("Username is required.", true);
        return;
    }

    try {
        const response = await fetch(USERNAME_ENDPOINT, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/x-www-form-urlencoded",
                Accept: "application/json"
            },
            body: new URLSearchParams({ username: newUsername })
        });

        ensureAuth(response);

        if (!response.ok) {
            const payload = await response.json().catch(() => ({}));
            throw new Error(payload.error || "Unable to update username.");
        }

        setUsernameMessage("Username updated successfully. You may need to log out and back in.", false);
        document.getElementById("profileUsername").textContent = newUsername;
        if (usernameInput) {
            usernameInput.value = newUsername;
        }
    } catch (error) {
        setUsernameMessage(error.message, true);
    }
}

async function handleEmailUpdate(event) {
    event.preventDefault();
    const newEmail = emailInput?.value?.trim();

    if (!newEmail) {
        setEmailMessage("Email is required.", true);
        return;
    }

    try {
        const response = await fetch(EMAIL_ENDPOINT, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/x-www-form-urlencoded",
                Accept: "application/json"
            },
            body: new URLSearchParams({ email: newEmail })
        });

        ensureAuth(response);

        if (!response.ok) {
            const payload = await response.json().catch(() => ({}));
            throw new Error(payload.error || "Unable to update email.");
        }

        setEmailMessage("Email updated successfully.", false);
        document.getElementById("profileEmail").textContent = newEmail;
    } catch (error) {
        setEmailMessage(error.message, true);
    }
}

function setUsernameMessage(message, isError = false) {
    if (!usernameMessage) {
        return;
    }
    usernameMessage.textContent = message;
    usernameMessage.classList.toggle("warning", Boolean(isError));
}

function setEmailMessage(message, isError = false) {
    if (!emailMessage) {
        return;
    }
    emailMessage.textContent = message;
    emailMessage.classList.toggle("warning", Boolean(isError));
}

function setMessage(message, isError = false) {
    const output = document.getElementById("profileMessage");
    if (!output) {
        return;
    }
    output.textContent = message;
    output.classList.toggle("warning", Boolean(isError));
}

function ensureAuth(response) {
    if (response.status === 401 || response.status === 403) {
        redirectToLogin();
        throw new Error("Authentication required");
    }
}
