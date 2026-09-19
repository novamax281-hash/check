(function () {
    "use strict";

    function svgIcon(name) {
        var map = {
            success: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>',
            error: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>',
            info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>',
            warning: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4M12 17h.01"/></svg>'
        };
        return map[name] || map.info;
    }

    function getContainer() {
        var container = document.getElementById("toastContainer");
        if (!container) {
            container = document.createElement("div");
            container.className = "toast-container";
            container.id = "toastContainer";
            document.body.appendChild(container);
        }
        return container;
    }

    function showToast(message, type) {
        type = type || "info";
        var container = getContainer();
        var toast = document.createElement("div");
        toast.className = "toast " + type;
        toast.setAttribute("role", "status");
        toast.innerHTML = svgIcon(type) + "<div>" + (message || "") + "</div>";
        container.appendChild(toast);

        var timer = setTimeout(function () {
            toast.classList.add("hide");
            setTimeout(function () {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 320);
        }, 3600);

        toast.addEventListener("click", function () {
            clearTimeout(timer);
            toast.classList.add("hide");
            setTimeout(function () {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 320);
        });

        return toast;
    }

    function openModal(el) {
        if (!el) return;
        el.classList.add("open");
        var focusable = el.querySelector("input, select, textarea, button:not(.modal-close)");
        if (focusable) focusable.focus();
    }

    function closeModal(el) {
        if (!el) return;
        el.classList.remove("open");
    }

    function setButtonLoading(btn, loading, loadingText) {
        if (!btn) return;
        if (loading) {
            btn.dataset.originalText = btn.textContent;
            btn.classList.add("loading");
            btn.innerHTML = '<span class="spinner"></span>' + (loadingText || "Loading...");
            btn.disabled = true;
        } else {
            btn.classList.remove("loading");
            btn.disabled = false;
            btn.textContent = btn.dataset.originalText || btn.textContent;
        }
    }

    function badge(status) {
        if (!status) return "Applied";
        var value = String(status);
        var map = {
            "PENDING": "Pending",
            "IN_PROGRESS": "InProgress",
            "QUALIFIED": "Qualified",
            "SELECTED": "Selected",
            "REJECTED": "Rejected",
            "WITHDRAWN": "Withdrawn",
            "APPLIED": "Applied",
            "UNDER REVIEW": "Under",
            "SHORTLISTED": "Shortlisted",
            "INTERVIEW SCHEDULED": "Interview",
            "OPEN": "Open",
            "CLOSED": "Closed"
        };
        var mapped = map[value.trim().toUpperCase()];
        if (mapped) return mapped;
        if (value === "Under Review") return "Under";
        if (value === "Interview Scheduled") return "Interview";
        return value.split(" ")[0];
    }

    function roundPills(app) {
        var byNumber = {};
        (app.rounds || []).forEach(function (entry) {
            if (entry && entry.round != null) byNumber[entry.round] = entry;
        });
        var total = Math.max(Number(app.totalRounds) || 1, Number(app.currentRound) || 1,
            Object.keys(byNumber).length || 1);
        var html = '<div class="round-track">';
        for (var i = 1; i <= total; i++) {
            var entry = byNumber[i];
            var cls = entry ? String(entry.status || "PENDING").toLowerCase() : "upcoming";
            if (i === Number(app.currentRound)) cls += " current";
            var title = "Round " + i;
            if (entry) {
                title += " · " + entry.status;
                if (entry.name) title += " · " + entry.name;
                if (entry.score != null) title += " · Score " + entry.score;
                if (entry.adminRemark) title += " · " + entry.adminRemark;
            } else if (i < Number(app.currentRound)) {
                title += " · completed";
            }
            html += '<span class="round-chip ' + cls + '" title="' + escapeHtml(title) + '">' + i + "</span>";
        }
        html += "</div>";
        var currentEntry = byNumber[Number(app.currentRound) || 1];
        var caption = "Round " + escapeHtml(String(app.currentRound || 1));
        if (currentEntry && currentEntry.name) caption += " — " + escapeHtml(currentEntry.name);
        html += '<div class="round-caption">' + caption + "</div>";
        return html;
    }

    function progressList(app) {
        var history = (app.rounds || []).slice().sort(function (a, b) {
            return (a.round || 0) - (b.round || 0);
        });
        var total = Math.max(Number(app.totalRounds) || 1, Number(app.currentRound) || 1, history.length);
        var rows = "";
        for (var i = 1; i <= total; i++) {
            var entry = null;
            for (var k = 0; k < history.length; k++) {
                if (history[k].round === i) entry = history[k];
            }
            var status = entry ? (entry.status || "PENDING") : "PENDING";
            var icon = status === "QUALIFIED" ? "✓" : status === "REJECTED" ? "✕" : "●";
            rows += '<div class="progress-row ' + status.toLowerCase()
                + (i === Number(app.currentRound) ? " current" : "") + '">'
                + '<span class="progress-icon">' + icon + "</span>"
                + '<div class="progress-info"><div class="progress-head">Round ' + i;
            if (entry && entry.name) rows += " – " + escapeHtml(entry.name);
            rows += ' <span class="badge ' + badge(status) + '">' + status + "</span></div>";
            if (entry && entry.score != null) rows += '<div class="progress-meta">Score: ' + escapeHtml(entry.score) + "</div>";
            if (entry && entry.adminRemark) rows += '<div class="progress-meta">Remark: ' + escapeHtml(entry.adminRemark) + "</div>";
            if (entry && entry.evaluatedAt) {
                rows += '<div class="progress-meta">Evaluated: '
                    + escapeHtml(String(entry.evaluatedAt).replace("T", " ").slice(0, 16)) + "</div>";
            }
            rows += "</div></div>";
        }
        return '<div class="progress-list">' + rows + "</div>";
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) return "";
        return String(value).replace(/[&<>"']/g, function (m) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[m];
        });
    }

    function confirmAction(message, options) {
        options = options || {};
        return new Promise(function (resolve) {
            var overlay = document.createElement("div");
            overlay.className = "modal";
            var box = document.createElement("div");
            box.className = "modal-box confirm-box";
            box.innerHTML = '<div class="modal-header"><h2>' + escapeHtml(options.title || "Are you sure?")
                + '</h2></div><p class="confirm-message">' + escapeHtml(message || "") + "</p>"
                + '<div class="modal-actions">'
                + '<button class="btn btn-secondary" data-act="cancel">' + escapeHtml(options.cancelText || "Cancel") + "</button>"
                + '<button class="btn ' + (options.danger ? "btn-danger" : "btn-success")
                + '" data-act="confirm">' + escapeHtml(options.confirmText || "Confirm") + "</button></div>";
            overlay.appendChild(box);
            document.body.appendChild(overlay);
            requestAnimationFrame(function () { overlay.classList.add("open"); });
            function done(result) {
                overlay.classList.remove("open");
                setTimeout(function () { if (overlay.parentNode) overlay.parentNode.removeChild(overlay); }, 200);
                resolve(result);
            }
            overlay.addEventListener("click", function (event) {
                var act = event.target.getAttribute && event.target.getAttribute("data-act");
                if (act === "confirm") done(true);
                else if (act === "cancel" || event.target === overlay) done(false);
            });
        });
    }

    function redirectIfUnauthorized(response) {
        if (response && response.status === 401) {
            window.location.href = "/login.html";
            return true;
        }
        return false;
    }

    function logout() {
        fetch("/logout", { method: "POST", credentials: "include" })
            .finally(function () {
                window.location.href = "/login.html";
            });
    }

    var profilePromise = null;

    function getProfile() {
        if (!profilePromise) {
            profilePromise = fetch("/user/profile", { credentials: "include" })
                .then(function (res) {
                    if (res.status === 401) return null;
                    return res.json().catch(function () { return null; });
                })
                .catch(function () { return null; });
        }
        return profilePromise;
    }

    function initials(name, enrollment) {
        var source = name || enrollment || "?";
        var parts = String(source).trim().split(/\s+/).filter(Boolean);
        if (!parts.length) return "?";
        if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
        return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    function fmtDateTime(value) {
        return value ? String(value).replace("T", " ").slice(0, 16) : "-";
    }

    function parseTimestamp(value) {
        if (!value) return null;
        var normalized = String(value).replace(" ", "T");
        var time = new Date(normalized).getTime();
        return Number.isNaN(time) ? null : time;
    }

    function batchYears(value) {
        var numbers = String(value || "").match(/\d{4}/g);
        if (!numbers) return null;
        var years = numbers.map(Number).sort(function (a, b) { return a - b; });
        return [years[0], years[years.length - 1]];
    }

    function batchMatches(appBatch, selected) {
        if (!appBatch || !selected) return false;
        var a = String(appBatch).trim();
        var s = String(selected).trim();
        if (a === s) return true;
        var selectedYear = Number((s.match(/\d{4}/) || [])[0]);
        var rangeA = batchYears(a);
        if (selectedYear && rangeA && selectedYear >= rangeA[0] && selectedYear <= rangeA[1]) return true;
        var rangeS = batchYears(s);
        if (rangeA && rangeS) {
            return rangeA[0] <= rangeS[1] && rangeS[0] <= rangeA[1];
        }
        return a.includes(s) || s.includes(a);
    }

    window.UI = {
        showToast: showToast,
        openModal: openModal,
        closeModal: closeModal,
        setButtonLoading: setButtonLoading,
        badge: badge,
        roundPills: roundPills,
        progressList: progressList,
        escapeHtml: escapeHtml,
        confirmAction: confirmAction,
        redirectIfUnauthorized: redirectIfUnauthorized,
        getProfile: getProfile,
        logout: logout,
        initials: initials,
        fmtDateTime: fmtDateTime,
        parseTimestamp: parseTimestamp,
        batchYears: batchYears,
        batchMatches: batchMatches
    };
})();

(function () {
    "use strict";

    var STORAGE_KEY = "portal-theme";
    var THEME_ATTR = "data-theme";

    var ICONS = {
        sun: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>',
        moon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>'
    };

    function getTheme() {
        return document.documentElement.getAttribute(THEME_ATTR) === "dark" ? "dark" : "light";
    }

    function hasStoredPreference() {
        try {
            return localStorage.getItem(STORAGE_KEY) !== null;
        } catch (e) {
            return false;
        }
    }

    function setTheme(theme) {
        if (theme !== "dark" && theme !== "light") theme = "light";
        document.documentElement.setAttribute(THEME_ATTR, theme);
        try {
            localStorage.setItem(STORAGE_KEY, theme);
        } catch (e) {}
        syncToggles();
    }

    function toggle() {
        setTheme(getTheme() === "dark" ? "light" : "dark");
    }

    function syncToggles() {
        var isDark = getTheme() === "dark";
        document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
            btn.setAttribute("aria-label", isDark ? "Switch to light mode" : "Switch to dark mode");
            btn.setAttribute("aria-pressed", String(isDark));
            btn.innerHTML = isDark ? ICONS.sun : ICONS.moon;
        });
    }

    window.ThemeManager = {
        getTheme: getTheme,
        setTheme: setTheme,
        toggle: toggle,
        hasStoredPreference: hasStoredPreference,
        sync: syncToggles
    };

    function init() {
        syncToggles();

        document.addEventListener("click", function (e) {
            var toggleBtn = e.target.closest("[data-theme-toggle]");
            if (toggleBtn) toggle();
        });

        var systemQuery = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)");
        if (systemQuery && systemQuery.addEventListener) {
            systemQuery.addEventListener("change", function (e) {
                if (!hasStoredPreference()) {
                    document.documentElement.setAttribute(THEME_ATTR, e.matches ? "dark" : "light");
                    syncToggles();
                }
            });
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();

(function () {
    "use strict";

    var ICONS = {
        dashboard: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>',
        briefcase: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>',
        list: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>',
        users: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
        logout: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5M21 12H9"/></svg>',
        settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
        menu: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h18M3 6h18M3 18h18"/></svg>',
        close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6L6 18M6 6l12 12"/></svg>',
        moon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>',
        sun: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>',
        chevron: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6"/></svg>'
    };

    var NAV = {
        student: [
            { href: "/dashboard.html", id: "dashboard", label: "Dashboard", icon: "dashboard" },
            { href: "/placement-apply.html", id: "placements", label: "Apply for Placements", icon: "briefcase" },
            { href: "/my-applications.html", id: "applications", label: "My Applications", icon: "list" },
            { href: "/profile.html", id: "profile", label: "My Profile", icon: "users" }
        ],
        admin: [
            { href: "/admin-dashboard.html", id: "dashboard", label: "Dashboard", icon: "dashboard" },
            { href: "/admin-students.html", id: "students", label: "Students", icon: "users" },
            { href: "/admin-applications.html", id: "applications", label: "View Applications", icon: "list" },
            { href: "/admin-placements.html", id: "placements", label: "Manage Placements", icon: "briefcase" },
            { href: "/profile.html", id: "profile", label: "My Profile", icon: "users" }
        ]
    };

    var TITLES = {
        "student.dashboard": "Dashboard",
        "student.placements": "Apply for Placements",
        "student.applications": "My Applications",
        "student.profile": "My Profile",
        "admin.dashboard": "Admin Dashboard",
        "admin.placements": "Manage Placements",
        "admin.applications": "View Applications",
        "admin.students": "Students",
        "admin.profile": "My Profile"
    };

    function getBodyConfig() {
        var body = document.body;
        return {
            role: body.getAttribute("data-role") || "student",
            active: body.getAttribute("data-active") || "dashboard",
            title: body.getAttribute("data-title") || ""
        };
    }

    function mapRole(userRole) {
        if (!userRole) return null;
        var value = String(userRole).toLowerCase();
        if (value === "admin" || value === "coordinator") return "admin";
        return "student";
    }

    function buildSidebar(config) {
        var nav = NAV[config.role] || NAV.student;
        var items = nav.map(function (item) {
            var active = item.id === config.active ? " active" : "";
            return '<a href="' + item.href + '" class="sidebar-item' + active + '">' +
                '<span class="item-icon">' + ICONS[item.icon] + "</span>" +
                '<span class="item-text">' + item.label + "</span></a>";
        }).join("");

        var brandText = config.role === "admin" ? "PlacementLink Admin" : "PlacementLink";

        return "" +
            '<div class="sidebar-brand">' +
                '<span class="sidebar-logo">' + ICONS.briefcase + "</span>" +
                '<span class="brand-text">' + brandText + "</span>" +
            "</div>" +
            '<nav class="sidebar-nav">' +
                '<div class="nav-label">Menu</div>' +
                items +
            "</nav>" +
            '<div class="sidebar-footer">' +
                '<div class="user-card">' +
                    '<span class="user-avatar" id="userAvatar">?</span>' +
                    '<div class="user-meta">' +
                        '<div class="user-name" id="userName">Loading...</div>' +
                        '<div class="user-role" id="userRole">' + config.role + "</div>" +
                    "</div>" +
                "</div>" +
                '<div class="user-actions">' +
                    '<button class="icon-btn" id="settingsBtn" data-open-settings title="Settings" aria-label="Settings">' + ICONS.settings + "</button>" +
                    '<button class="icon-btn danger" id="logoutBtn" title="Logout" aria-label="Logout">' + ICONS.logout + "</button>" +
                "</div>" +
            "</div>";
    }

    function buildHeader(config) {
        var title = config.title || TITLES[config.role + "." + config.active] || "Dashboard";
        return "" +
            '<button class="hamburger" id="sidebarToggle" aria-label="Toggle navigation">' + ICONS.menu + "</button>" +
            '<div class="header-title" id="headerTitle">' + title + "</div>" +
            '<div class="header-spacer"></div>' +
            '<div class="header-actions">' +
                '<button class="theme-toggle" data-theme-toggle aria-label="Toggle theme"></button>' +
            "</div>";
    }

    function buildSettingsModal() {
        var modal = document.createElement("div");
        modal.className = "modal";
        modal.id = "settingsModal";
        modal.innerHTML = "" +
            '<div class="modal-box">' +
                '<div class="modal-header">' +
                    "<h2>Settings</h2>" +
                    '<button class="modal-close" data-close-settings aria-label="Close">' + ICONS.close + "</button>" +
                "</div>" +
                '<div class="settings-section">' +
                    '<div class="nav-label" style="padding:0 0 8px">Appearance</div>' +
                    '<div class="theme-row">' +
                        '<span class="theme-label">Theme</span>' +
                        '<div class="theme-switcher">' +
                            '<button class="theme-option light" data-theme-option="light">' + ICONS.sun + " Light</button>" +
                            '<button class="theme-option dark" data-theme-option="dark">' + ICONS.moon + " Dark</button>" +
                        "</div>" +
                    "</div>" +
                "</div>" +
                '<div class="settings-section">' +
                    '<div class="nav-label" style="padding:0 0 8px">Account</div>' +
                    '<div class="detail-list" id="accountDetail">' +
                        "<p><strong>Name</strong><span id=\"accName\">-</span></p>" +
                        "<p><strong>Enrollment</strong><span id=\"accEnrollment\">-</span></p>" +
                        "<p><strong>Email</strong><span id=\"accEmail\">-</span></p>" +
                        "<p><strong>Phone</strong><span id=\"accPhone\">-</span></p>" +
                        "<p><strong>Role</strong><span id=\"accRole\">-</span></p>" +
                    "</div>" +
                "</div>" +
                '<div class="modal-actions">' +
                    '<button class="btn" data-close-settings>Done</button>' +
                "</div>" +
            "</div>";
        document.body.appendChild(modal);
        return modal;
    }

    function loadProfile() {
        window.UI.getProfile()
            .then(function (user) {
                var name = (user && user.name) || "";
                var enrollment = (user && user.enrollmentno) || "";
                var role = (user && user.role) || document.body.getAttribute("data-role") || "Student";
                var email = (user && user.email) || "";
                var phone = (user && user.phoneno) || "";

                var avatar = document.getElementById("userAvatar");
                var nameEl = document.getElementById("userName");
                var roleEl = document.getElementById("userRole");
                if (avatar) avatar.textContent = window.UI.initials(name || enrollment || role);
                if (nameEl) nameEl.textContent = name || enrollment || role;
                if (roleEl) roleEl.textContent = role;

                var accName = document.getElementById("accName");
                if (accName) accName.textContent = name || "-";
                document.getElementById("accEnrollment").textContent = enrollment || "-";
                document.getElementById("accEmail").textContent = email || "-";
                document.getElementById("accPhone").textContent = phone || "-";
                document.getElementById("accRole").textContent = role || "-";

                var current = getBodyConfig();
                var mapped = mapRole(role);
                if (mapped && mapped !== current.role) {
                    current.role = mapped;
                    render(current);
                }
            });
    }

    var settingsModal = null;
    var boundGlobal = false;

    function syncThemeOptions() {
        var current = window.ThemeManager.getTheme();
        document.querySelectorAll("[data-theme-option]").forEach(function (btn) {
            var active = btn.getAttribute("data-theme-option") === current;
            btn.classList.toggle("active", active);
        });
    }

    function ensureGlobalHandlers() {
        if (boundGlobal) return;
        boundGlobal = true;
        settingsModal = buildSettingsModal();

        document.addEventListener("click", function (e) {
            if (e.target.closest("[data-open-settings]")) {
                loadProfile();
                syncThemeOptions();
                window.UI.openModal(settingsModal);
                return;
            }
            if (e.target.closest("[data-close-settings]") || e.target === settingsModal) {
                window.UI.closeModal(settingsModal);
            }
        });

        document.addEventListener("click", function (e) {
            var option = e.target.closest("[data-theme-option]");
            if (option) {
                window.ThemeManager.setTheme(option.getAttribute("data-theme-option"));
                syncThemeOptions();
            }
        });

        document.body.addEventListener("click", function (e) {
            if (e.target.closest("#sidebarToggle")) {
                try {
                    localStorage.setItem("portal-sidebar",
                        document.body.classList.contains("sidebar-collapsed") ? "collapsed" : "expanded");
                } catch (err) {}
            }
        });

        window.addEventListener("resize", function () {
            closeMobile();
        });
    }

    function setupEvents(config) {
        var sidebar = document.getElementById("sidebar");
        var toggleBtn = document.getElementById("sidebarToggle");

        if (toggleBtn) {
            var toggleSidebar = function () {
                if (isMobile()) {
                    var open = sidebar.classList.contains("mobile-open");
                    open ? closeMobile() : openMobile();
                } else {
                    document.body.classList.toggle("sidebar-collapsed");
                }
            };
            toggleBtn.addEventListener("click", toggleSidebar);
        }

        var logoutBtn = document.getElementById("logoutBtn");
        if (logoutBtn) logoutBtn.addEventListener("click", function () {
            window.UI.logout();
        });

        ensureGlobalHandlers();
    }

    function isMobile() {
        return window.innerWidth <= 767;
    }

    function openMobile() {
        var sidebar = document.getElementById("sidebar");
        var overlay = document.getElementById("sidebarOverlay");
        if (!sidebar) return;
        document.body.classList.add("sidebar-collapsed");
        sidebar.classList.add("mobile-open");
        if (overlay) overlay.classList.add("show");
    }

    function closeMobile() {
        var sidebar = document.getElementById("sidebar");
        var overlay = document.getElementById("sidebarOverlay");
        if (sidebar) sidebar.classList.remove("mobile-open");
        if (overlay) overlay.classList.remove("show");
    }

    function render(config) {
        var sidebar = document.getElementById("sidebar");
        var header = document.getElementById("appHeader");

        if (!sidebar) return;
        if (!header) return;

        sidebar.innerHTML = buildSidebar(config);
        header.innerHTML = buildHeader(config);

        if (!document.getElementById("sidebarOverlay")) {
            var overlay = document.createElement("div");
            overlay.className = "sidebar-overlay";
            overlay.id = "sidebarOverlay";
            overlay.addEventListener("click", closeMobile);
            document.body.insertBefore(overlay, document.body.firstChild);
        }

        try {
            document.body.setAttribute("data-role", config.role);
        } catch (e) {}

        var stored = null;
        try { stored = localStorage.getItem("portal-sidebar"); } catch (e) {}
        var width = window.innerWidth;
        if (width <= 767) {
            closeMobile();
        } else if (width <= 1024) {
            if (stored !== "expanded") document.body.classList.add("sidebar-collapsed");
        } else {
            if (stored === "collapsed") document.body.classList.add("sidebar-collapsed");
        }

        setupEvents(config);
    }

    function init() {
        render(getBodyConfig());
        loadProfile();
        if (window.ThemeManager) window.ThemeManager.sync();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
