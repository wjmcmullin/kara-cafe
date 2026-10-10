document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(window.location.search);
    const nextPath = sanitizeNextPath(params.get("next"));

    const token = localStorage.getItem("karaToken");
    if (token) {
        window.location.replace(nextPath || "karaoke-room/");
        return;
    }

    const form = document.getElementById("loginForm");
    const feedback = document.getElementById("feedback");

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        feedback.textContent = "";

        const submitButton = form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.disabled = true;
        }

        const formData = new URLSearchParams(new FormData(form));
        try {
            const response = await fetch("/kara-cafe/api/auth/login", {
                method: "POST",
                headers: {
                    Accept: "application/json"
                },
                body: formData
            });

            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                feedback.textContent = data.error || "Login failed. Try again.";
                return;
            }

            const payload = await response.json();
            localStorage.setItem("karaToken", payload.token);
            window.location.href = nextPath || "karaoke-room/";
        } catch (error) {
            feedback.textContent = "Unable to log in. Try again.";
        } finally {
            if (submitButton) {
                submitButton.disabled = false;
            }
        }
    });
});

function sanitizeNextPath(nextPath) {
    if (!nextPath || typeof nextPath !== "string") {
        return null;
    }

    if (!nextPath.startsWith("/") || nextPath.startsWith("//")) {
        return null;
    }

    return nextPath;
}
