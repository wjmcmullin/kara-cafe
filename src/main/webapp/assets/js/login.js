document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("loginForm");
    const feedback = document.getElementById("feedback");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        feedback.textContent = "";

        const formData = new URLSearchParams(new FormData(form));
        const response = await fetch("/kara-cafe/api/auth/login", {
            method: "POST",
            headers: {
                "Accept": "application/json"
            },
            body: formData
        });

        if (response.ok) {
            const payload = await response.json();
            const token = payload.token;
            localStorage.setItem("karaToken", token);
            window.location.href = "karaoke-room/";
        } else {
            const errorPayload = await response.json().catch(() => ({ error: "Unable to log in" }));
            feedback.textContent = errorPayload.error || "Login failed. Try again.";
        }
    });
});
