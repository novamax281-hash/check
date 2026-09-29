/**
 * Student notice carousel.
 *
 * Design notes:
 * - Notices are fetched once by the caller and handed to Notices.set(). Moving between
 *   notices is a pure CSS transform on one track, so no request is made per slide.
 * - Mouse drag and touch swipe are both handled by the same pointer-event path.
 * - Autoplay is a slow 7s drift that stops on hover, on focus, and while the tab is
 *   hidden, and is disabled entirely under prefers-reduced-motion. Arrows, dots and
 *   swipe remain available regardless.
 * - A single notice renders as a plain card with no carousel chrome, per spec.
 */
(function () {
    "use strict";

    var AUTOPLAY_MS = 7000;
    var SWIPE_THRESHOLD = 45;

    var el = {};
    var notices = [];
    var index = 0;
    var timer = null;
    var drag = null;
    var bound = false;

    function byId(id) { return document.getElementById(id); }

    function cacheElements() {
        el.panel = byId("noticePanel");
        el.skeleton = byId("noticeSkeleton");
        el.empty = byId("noticeEmpty");
        el.error = byId("noticeError");
        el.retry = byId("noticeRetry");
        el.carousel = byId("noticeCarousel");
        el.viewport = byId("noticeViewport");
        el.track = byId("noticeTrack");
        el.controls = byId("noticeControls");
        el.dots = byId("noticeDots");
        el.prev = byId("noticePrev");
        el.next = byId("noticeNext");
        el.count = byId("noticeCount");
    }

    function show(node, visible) {
        if (node) node.classList.toggle("hidden", !visible);
    }

    function relativeTime(value) {
        if (!value) return "";
        var stamp = UI.parseTimestamp(value);
        if (!stamp) return "";
        var seconds = Math.floor((Date.now() - stamp) / 1000);
        if (seconds < 60) return "just now";
        var minutes = Math.floor(seconds / 60);
        if (minutes < 60) return minutes + " minute" + (minutes === 1 ? "" : "s") + " ago";
        var hours = Math.floor(minutes / 60);
        if (hours < 24) return hours + " hour" + (hours === 1 ? "" : "s") + " ago";
        var days = Math.floor(hours / 24);
        if (days < 7) return days + " day" + (days === 1 ? "" : "s") + " ago";
        return UI.fmtDate(value);
    }

    function isFresh(value) {
        var stamp = UI.parseTimestamp(value);
        if (!stamp) return false;
        return Date.now() - stamp < 24 * 60 * 60 * 1000;
    }

    function noticeTemplate(notice, position) {
        var esc = UI.escapeHtml;
        var category = notice.category
            ? '<span class="notice-tag">' + esc(notice.category) + "</span>"
            : "";
        var fresh = isFresh(notice.createdAt)
            ? '<span class="notice-new">New</span>'
            : "";
        // The link is omitted entirely when absent rather than rendering a dead control.
        var link = notice.link
            ? '<a class="notice-link" href="' + esc(notice.link) + '" target="_blank" rel="noopener noreferrer">'
              + "View details &rarr;</a>"
            : "";
        var when = relativeTime(notice.createdAt);
        return '<div class="notice-slide" role="group" aria-roledescription="slide"'
            + ' aria-label="Notice ' + (position + 1) + ' of ' + notices.length + '">'
            + '<article class="notice-card">'
            + '<div class="notice-card-top">'
            + '<h3 class="notice-title">' + esc(notice.title || "Notice") + "</h3>"
            + category
            + "</div>"
            + '<p class="notice-body">' + esc(notice.message || "") + "</p>"
            + link
            + '<div class="notice-meta">' + esc(when) + "</div>"
            + fresh
            + "</article></div>";
    }

    function render() {
        el.track.innerHTML = notices.map(noticeTemplate).join("");
        renderDots();
        index = Math.min(index, notices.length - 1);
        applyTransform(false);
        updateArrows();
    }

    function renderDots() {
        if (notices.length < 2) {
            el.dots.innerHTML = "";
            return;
        }
        el.dots.innerHTML = notices.map(function (notice, position) {
            return '<button type="button" class="notice-dot" data-index="' + position + '"'
                + ' aria-label="Notice ' + (position + 1) + ' of ' + notices.length + '"></button>';
        }).join("");
    }

    function applyTransform(animate) {
        if (animate) {
            el.track.classList.remove("no-anim");
            void el.track.offsetWidth; // reflow so the transition replays on every move
        } else {
            el.track.classList.add("no-anim");
        }
        el.track.style.transform = "translateX(-" + (index * 100) + "%)";
        Array.prototype.forEach.call(el.dots.children, function (dot, position) {
            dot.classList.toggle("active", position === index);
        });
    }

    function updateArrows() {
        var many = notices.length > 1;
        el.prev.disabled = !many;
        el.next.disabled = !many;
    }

    function goTo(target) {
        if (notices.length < 2) return;
        index = (target + notices.length) % notices.length;
        applyTransform(true);
    }

    function step(delta) {
        goTo(index + delta);
    }

    function showLoading() {
        show(el.skeleton, true);
        show(el.empty, false);
        show(el.error, false);
        show(el.carousel, false);
        show(el.count, false);
    }

    function showEmpty() {
        show(el.skeleton, false);
        show(el.empty, true);
        show(el.error, false);
        show(el.carousel, false);
        show(el.count, true);
        if (el.count) el.count.textContent = "0";
    }

    function showError() {
        show(el.skeleton, false);
        show(el.empty, false);
        show(el.error, true);
        show(el.carousel, false);
        show(el.count, false);
    }

    function showNotices() {
        show(el.skeleton, false);
        show(el.empty, false);
        show(el.error, false);
        show(el.carousel, true);
        show(el.count, true);
        if (el.count) el.count.textContent = String(notices.length);
        // Carousel chrome only makes sense with more than one notice.
        show(el.controls, notices.length > 1);
    }

    function onPointerDown(event) {
        if (notices.length < 2) return;
        if (event.button !== undefined && event.button !== 0) return;
        drag = { startX: event.clientX, startY: event.clientY, delta: 0, active: true };
        el.viewport.classList.add("is-dragging");
        el.track.classList.add("no-anim");
        stopAutoplay();
    }

    function onPointerMove(event) {
        if (!drag || !drag.active) return;
        var dx = event.clientX - drag.startX;
        var dy = event.clientY - drag.startY;
        // Only engage once the gesture is clearly horizontal, so vertical page
        // scrolling is not hijacked on touch devices.
        if (Math.abs(dx) < 6 && Math.abs(dx) < Math.abs(dy)) return;
        drag.delta = dx;
        el.track.style.transform =
            "translateX(calc(-" + (index * 100) + "% + " + dx + "px))";
        if (event.cancelable) event.preventDefault();
    }

    function onPointerUp() {
        if (!drag || !drag.active) return;
        var delta = drag.delta;
        drag = null;
        el.viewport.classList.remove("is-dragging");
        var width = el.viewport.offsetWidth || 1;
        if (Math.abs(delta) > SWIPE_THRESHOLD || Math.abs(delta) > width * 0.18) {
            step(delta < 0 ? 1 : -1);
        } else {
            applyTransform(true);
        }
        startAutoplay();
    }

    function startAutoplay() {
        stopAutoplay();
        if (notices.length < 2) return;
        if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
        timer = setInterval(function () { step(1); }, AUTOPLAY_MS);
    }

    function stopAutoplay() {
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
    }

    function bindOnce() {
        if (bound || !el.viewport) return;
        bound = true;

        el.next.addEventListener("click", function () { step(1); startAutoplay(); });
        el.prev.addEventListener("click", function () { step(-1); startAutoplay(); });

        el.dots.addEventListener("click", function (event) {
            var dot = event.target.closest(".notice-dot");
            if (!dot) return;
            goTo(Number(dot.getAttribute("data-index")));
            startAutoplay();
        });

        el.viewport.addEventListener("pointerdown", onPointerDown);
        el.viewport.addEventListener("pointermove", onPointerMove);
        el.viewport.addEventListener("pointerup", onPointerUp);
        el.viewport.addEventListener("pointercancel", onPointerUp);
        el.viewport.addEventListener("pointerleave", onPointerUp);

        el.viewport.addEventListener("keydown", function (event) {
            if (event.key === "ArrowRight") { step(1); startAutoplay(); }
            if (event.key === "ArrowLeft") { step(-1); startAutoplay(); }
        });

        // Pause while the user is reading or interacting.
        el.panel.addEventListener("mouseenter", stopAutoplay);
        el.panel.addEventListener("mouseleave", startAutoplay);
        el.panel.addEventListener("focusin", stopAutoplay);
        document.addEventListener("visibilitychange", function () {
            document.hidden ? stopAutoplay() : startAutoplay();
        });
    }

    var Notices = {
        showLoading: showLoading,
        showEmpty: showEmpty,
        showError: showError,

        set: function (list) {
            if (!el.panel) cacheElements();
            notices = Array.isArray(list) ? list : [];
            stopAutoplay();
            if (notices.length === 0) {
                showEmpty();
                return;
            }
            index = 0;
            render();
            showNotices();
            bindOnce();
            startAutoplay();
        }
    };

    window.Notices = Notices;
})();
