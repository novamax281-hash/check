const welcome = document.getElementById("welcome");
const recentBody = document.getElementById("recentBody");
const recentEmpty = document.getElementById("recentEmpty");

const statFields = [
    ["statTotal", "total"],
    ["statUnderReview", "underReview"],
    ["statShortlisted", "shortlisted"],
    ["statSelected", "selected"],
    ["statRejected", "rejected"]
];

async function loadProfile() {
    try {
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
        const response = await fetch("/user/applications", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) return;
        const applications = await response.json();
        if (!applications || applications.length === 0) {
            recentEmpty.classList.remove("hidden");
            return;
        }
        recentBody.innerHTML = applications.slice(0, 5).map(function(app) {
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

document.addEventListener("DOMContentLoaded", function() {
    loadProfile();
    loadStats();
    loadRecent();
});