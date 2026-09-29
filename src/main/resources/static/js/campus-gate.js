(function () {
    "use strict";

    var gate = document.getElementById("campusGate");
    var hero = document.querySelector(".lp-hero-wrapper");

    if (!gate || !hero) return;

    gate.addEventListener("click", function () {
        hero.classList.add("gate-open");
        gate.setAttribute("aria-expanded", "true");
    }, { once: true });
}());