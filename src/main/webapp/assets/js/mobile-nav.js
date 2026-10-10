document.addEventListener("DOMContentLoaded", () => {
    const navs = document.querySelectorAll(".room-nav");

    navs.forEach((nav) => {
        const toggle = nav.querySelector(".nav-toggle");
        const links = nav.querySelector(".room-nav-links");
        if (!toggle || !links) {
            return;
        }

        const setOpen = (open) => {
            links.classList.toggle("open", open);
            toggle.setAttribute("aria-expanded", open ? "true" : "false");
        };

        setOpen(false);

        toggle.addEventListener("click", () => {
            setOpen(!links.classList.contains("open"));
        });

        links.addEventListener("click", (event) => {
            const target = event.target;
            if (target instanceof HTMLElement && (target.closest("a") || target.closest("button"))) {
                setOpen(false);
            }
        });

        window.addEventListener("resize", () => {
            if (window.innerWidth > 640) {
                setOpen(false);
            }
        });
    });
});
