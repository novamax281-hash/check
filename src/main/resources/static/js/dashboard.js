const welcome = document.getElementById("welcome");
const recentBody = document.getElementById("recentBody");
const recentEmpty = document.getElementById("recentEmpty");

const RECENT_LIMIT = 5;

const statFields = [
    ["statTotal", "total"],
    ["statUnderReview", "underReview"],
    ["statShortlisted", "shortlisted"],
    ["statSelected", "selected"],
    ["statRejected", "rejected"]
];

async function loadProfile() {
    try {
        // UI.getProfile memoises its promise, so this shares one /user/profile
        // request with the sidebar header instead of issuing a second one.
        const user = await UI.getProfile();
        if (user) {
            welcome.textContent = "Welcome, " + (user.name || user.enrollmentno) + "!";
        }
    } catch (e) {
    }
}

async function loadStats() {
    try {
        const response = await fetch("/user/dashboard-stats", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) return;
        const stats = await response.json();
        statFields.forEach(function([elementId, key]) {
            document.getElementById(elementId).textContent = stats[key] || 0;
        });
    } catch (e) {
    }
}

async function loadRecent() {
    try {
        // Only the rows that are actually rendered are requested and mapped.
        const response = await fetch("/user/applications?limit=" + RECENT_LIMIT, { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) return;
        const applications = await response.json();
        if (!applications || applications.length === 0) {
            recentEmpty.classList.remove("hidden");
            return;
        }
        recentBody.innerHTML = applications.slice(0, RECENT_LIMIT).map(function(app) {
            const esc = window.UI.escapeHtml;
            return `
                <tr>
                    <td>${esc(app.company || "-")}</td>
                    <td>${esc(app.role || "-")}</td>
                    <td>${esc(app.salary || "-")}</td>
                    <td><span class="badge ${window.UI.badge(app.status)}">${esc(app.status || "Applied")}</span></td>
                    <td>${esc(window.UI.fmtDateTime(app.applicationDate))}</td>
                </tr>
            `;
        }).join("");
    } catch (e) {
    }
}

async function loadNotices() {
    if (!window.Notices) return;
    Notices.showLoading();
    try {
        const response = await fetch("/api/notices", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) {
            Notices.showError();
            return;
        }
        const notices = await response.json();
        // Rendered once; the carousel then runs entirely on the client.
        Notices.set(Array.isArray(notices) ? notices : []);
    } catch (e) {
        Notices.showError();
    }
}

document.addEventListener("DOMContentLoaded", function() {
    loadProfile();
    loadStats();
    loadRecent();
    loadNotices();

    const retry = document.getElementById("noticeRetry");
    if (retry) {
        retry.addEventListener("click", function() { loadNotices(); });
    }
});
