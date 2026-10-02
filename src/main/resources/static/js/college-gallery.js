(function () {
    "use strict";

    var lightbox = document.getElementById("collegeLightbox");
    var lightboxImage = document.getElementById("collegeLightboxImage");
    var lightboxCount = document.getElementById("collegeLightboxCount");
    var closeButton = document.getElementById("collegeLightboxClose");
    var previousButton = document.getElementById("collegeLightboxPrev");
    var nextButton = document.getElementById("collegeLightboxNext");
    var galleryItems = Array.prototype.slice.call(document.querySelectorAll(".college-gallery-item"));
    var animatedItems = galleryItems.filter(function (item) {
        return !item.classList.contains("static-gallery-item");
    });
    var activeGalleryItems = [];
    var currentIndex = 0;
    var stackOffset = 0;
    var stackTimer = null;
    var lastFocusedElement = null;
        var reducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)");
    var stackPositions = [
        { x: "0px", y: "0px", rotate: "0deg" },
        { x: "14px", y: "8px", rotate: "1.4deg" },
        { x: "-14px", y: "16px", rotate: "-1.6deg" },
        { x: "20px", y: "24px", rotate: "2deg" },
        { x: "-20px", y: "32px", rotate: "-2.2deg" },
        { x: "26px", y: "40px", rotate: "2.6deg" },
        { x: "-26px", y: "48px", rotate: "-2.8deg" }
    ];

    if (!lightbox || !lightboxImage || !galleryItems.length) return;

    function updateStack() {
        animatedItems.forEach(function (item, index) {
            var positionIndex = (index - stackOffset + animatedItems.length) % animatedItems.length;
            var position = stackPositions[positionIndex % stackPositions.length];
            item.style.setProperty("--stack-x", position.x);
            item.style.setProperty("--stack-y", position.y);
            item.style.setProperty("--stack-rotate", position.rotate);
            item.style.zIndex = String(animatedItems.length - positionIndex);
        });
    }

    function rotateStack() {
        stackOffset = (stackOffset + 1) % animatedItems.length;
        updateStack();
    }

    function startStackAnimation() {
        if (!stackTimer && animatedItems.length && !(reducedMotion && reducedMotion.matches)) {
            stackTimer = window.setInterval(rotateStack, 6000);
        }
    }

    function stopStackAnimation() {
        if (stackTimer) {
            window.clearInterval(stackTimer);
            stackTimer = null;
        }
    }

    function showImage(index) {
        currentIndex = (index + activeGalleryItems.length) % activeGalleryItems.length;
        var image = activeGalleryItems[currentIndex].querySelector("img");
        lightboxImage.src = image.src;
        lightboxImage.alt = image.alt;
        lightboxCount.textContent = (currentIndex + 1) + " / " + activeGalleryItems.length;
    }

    function openLightbox(item) {
        lastFocusedElement = document.activeElement;
        stopStackAnimation();
        var gallery = item.closest(".college-gallery, .static-gallery");
        activeGalleryItems = Array.prototype.slice.call(gallery.querySelectorAll(".college-gallery-item"));
        showImage(activeGalleryItems.indexOf(item));
        lightbox.classList.add("open");
        lightbox.setAttribute("aria-hidden", "false");
        document.documentElement.classList.add("lightbox-open");
        document.body.classList.add("lightbox-open");
        closeButton.focus();
    }

    function closeLightbox() {
        lightbox.classList.remove("open");
        lightbox.setAttribute("aria-hidden", "true");
        document.documentElement.classList.remove("lightbox-open");
        document.body.classList.remove("lightbox-open");
        if (lastFocusedElement) lastFocusedElement.focus();
        startStackAnimation();
    }

    galleryItems.forEach(function (item) {
        item.addEventListener("click", function () {
            openLightbox(item);
        });
    });

    closeButton.addEventListener("click", closeLightbox);
    previousButton.addEventListener("click", function () { showImage(currentIndex - 1); });
    nextButton.addEventListener("click", function () { showImage(currentIndex + 1); });

    lightbox.addEventListener("click", function (event) {
        if (event.target === lightbox) closeLightbox();
    });

    document.addEventListener("keydown", function (event) {
        if (!lightbox.classList.contains("open")) return;
        if (event.key === "Escape") closeLightbox();
        if (event.key === "ArrowLeft") showImage(currentIndex - 1);
        if (event.key === "ArrowRight") showImage(currentIndex + 1);
    });

    if (reducedMotion && reducedMotion.addEventListener) {
        reducedMotion.addEventListener("change", function (event) {
            if (event.matches) stopStackAnimation();
            else startStackAnimation();
        });
    }

    updateStack();
    startStackAnimation();
}());
