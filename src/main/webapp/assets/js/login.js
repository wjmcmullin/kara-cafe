document.addEventListener("DOMContentLoaded", () => {
    const token = localStorage.getItem("karaToken");
    if (token) {
        window.location.replace("karaoke-room/");
        return;
    }

    const form = document.getElementById("loginForm");
    const feedback = document.getElementById("feedback");

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        feedback.textContent = "";

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
            window.location.href = "karaoke-room/";
        } catch (error) {
            feedback.textContent = "Unable to log in. Try again.";
        }
    });
});
