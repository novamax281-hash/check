const welcome = document.getElementById("welcome");
const recentBody = document.getElementById("recentBody");
const recentEmpty = document.getElementById("recentEmpty");
const recentTable = document.getElementById("recentTable");
const recentError = document.getElementById("recentError");
const recentErrorMessage = document.getElementById("recentErrorMessage");
const statsGrid = document.getElementById("statsGrid");
const statsLoading = document.getElementById("statsLoading");
const statsError = document.getElementById("statsError");
const statsErrorMessage = document.getElementById("statsErrorMessage");

const RECENT_LIMIT = 5;
let initialized = false;
let noticesRequestInFlight = false;

const statFields = [
    ["statTotal", "total"],
    ["statInProgress", "inProgress"],
    ["statShortlisted", "shortlisted"],
    ["statInterviewScheduled", "interviewScheduled"],
    ["statSelected", "selected"],
    ["statRejected", "rejected"],
    ["statWithdrawn", "withdrawn"]
];

async function loadProfile() {
    try {
        // UI.getProfile memoises its promise, so this shares one /user/profile
        // request with the sidebar header instead of issuing a second one.
        const user = await UI.getProfile();
        if (user) {
            welcome.textContent = "Welcome, " + (user.name || user.enrollmentno) + "!";
        }
    } catch (error) {
        console.warn("[Dashboard] Profile request failed");
    }
}

class DashboardRequestError extends Error {
    constructor(message, status, kind) {
        super(message);
        this.status = status;
        this.kind = kind;
    }
}

async function dashboardGetJson(url, label) {
    console.info("[Dashboard] Loading " + label);
    let response;
    try {
        response = await fetch(url, {
            method: "GET",
            credentials: "include",
            headers: { Accept: "application/json" }
        });
    } catch (error) {
        console.warn("[Dashboard] " + label + " request failed: network error");
        throw new DashboardRequestError("Network request failed", 0, "network");
    }

    if (response.status === 401) {
        UI.redirectIfUnauthorized(response);
        throw new DashboardRequestError("Authentication required", 401, "unauthorized");
    }
    if (!response.ok) {
        console.warn("[Dashboard] " + label + " request failed (HTTP " + response.status + ")");
        throw new DashboardRequestError("HTTP " + response.status, response.status, "http");
    }

    const contentType = response.headers.get("Content-Type") || "";
    if (!contentType.toLowerCase().includes("application/json")
            && !contentType.toLowerCase().includes("+json")) {
        console.warn("[Dashboard] " + label + " response had unexpected Content-Type");
        throw new DashboardRequestError("Unexpected response format", response.status, "format");
    }
    let data;
    try {
        data = await response.json();
    } catch (error) {
        console.warn("[Dashboard] " + label + " response was not valid JSON");
        throw new DashboardRequestError("Invalid JSON response", response.status, "json");
    }
    console.info("[Dashboard] " + label + " response: " + response.status);
    return data;
}

function sectionErrorMessage(error, label) {
    if (error.kind === "unauthorized") return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You are not authorized to view " + label + ".";
    if (error.status === 404) return label + " could not be found for this account.";
    return "Unable to load " + label + ". Please try again.";
}

function showStatsLoading() {
    statsLoading.classList.remove("hidden");
    statsGrid.classList.add("hidden");
    statsError.classList.add("hidden");
}

function showStatsError(error) {
    statsLoading.classList.add("hidden");
    statsGrid.classList.add("hidden");
    statsError.classList.remove("hidden");
    statsErrorMessage.textContent = sectionErrorMessage(error, "application statistics");
}

async function loadStats() {
    showStatsLoading();
    try {
        const stats = await dashboardGetJson("/user/dashboard-stats", "statistics");
        if (!stats || typeof stats !== "object" || Array.isArray(stats)
                || statFields.some(function([, key]) {
                    return typeof stats[key] !== "number" || !Number.isFinite(stats[key]);
                })) {
            throw new DashboardRequestError("Unexpected statistics response", 200, "format");
        }
        statFields.forEach(function([elementId, key]) {
            document.getElementById(elementId).textContent = String(stats[key]);
        });
        statsLoading.classList.add("hidden");
        statsError.classList.add("hidden");
        statsGrid.classList.remove("hidden");
    } catch (error) {
        if (error.kind !== "unauthorized") showStatsError(error);
    }
}

function showRecentLoading() {
    recentEmpty.classList.add("hidden");
    recentError.classList.add("hidden");
    recentTable.classList.remove("hidden");
    recentBody.innerHTML = Array.from({ length: RECENT_LIMIT }, function() {
        return '<tr class="dashboard-skeleton-row"><td><span></span></td><td><span></span></td>'
            + '<td><span></span></td><td><span></span></td><td><span></span></td></tr>';
    }).join("");
}

function showRecentError(error) {
    recentTable.classList.add("hidden");
    recentEmpty.classList.add("hidden");
    recentError.classList.remove("hidden");
    recentErrorMessage.textContent = sectionErrorMessage(error, "recent applications");
}

async function loadRecent() {
    showRecentLoading();
    try {
        const applications = await dashboardGetJson(
                "/user/applications?limit=" + RECENT_LIMIT, "recent applications");
        if (!Array.isArray(applications)) {
            throw new DashboardRequestError("Expected an application array", 200, "format");
        }
        recentBody.innerHTML = applications.slice(0, RECENT_LIMIT).map(function(app) {
            const esc = window.UI.escapeHtml;
            return `
                <tr>
                    <td>${esc(app.company || "-")}</td>
                    <td>${esc(app.role || "-")}</td>
                    <td>${esc(app.salary || app.ctc || "-")}</td>
                    <td><span class="badge ${window.UI.badge(app.status)}">${esc(app.status || "Applied")}</span></td>
                    <td>${esc(window.UI.fmtDateTime(app.applicationDate))}</td>
                </tr>
            `;
        }).join("");
        if (applications.length === 0) {
            recentTable.classList.add("hidden");
            recentEmpty.classList.remove("hidden");
            console.info("[Dashboard] Loaded 0 recent applications");
            return;
        }
        recentTable.classList.remove("hidden");
        recentEmpty.classList.add("hidden");
        console.info("[Dashboard] Loaded " + Math.min(applications.length, RECENT_LIMIT)
                + " recent applications");
    } catch (error) {
        if (error.kind !== "unauthorized") showRecentError(error);
    }
}

async function loadNotices() {
    if (!window.Notices || noticesRequestInFlight) return;
    noticesRequestInFlight = true;
    Notices.showLoading();
    try {
        const notices = await dashboardGetJson("/api/notices", "notices");
        if (!Array.isArray(notices)) {
            throw new DashboardRequestError("Expected a notice array", 200, "format");
        }
        Notices.set(notices);
        console.info("[Dashboard] Loaded " + notices.length + " notices");
    } catch (error) {
        if (error.kind !== "unauthorized") Notices.showError();
    } finally {
        noticesRequestInFlight = false;
    }
}

document.addEventListener("DOMContentLoaded", function() {
    if (initialized) return;
    initialized = true;
    loadProfile();
    loadStats();
    loadRecent();
    loadNotices();

    document.getElementById("statsRetry").addEventListener("click", loadStats);
    document.getElementById("recentRetry").addEventListener("click", loadRecent);
    const retry = document.getElementById("noticeRetry");
    if (retry) {
        retry.addEventListener("click", function() { loadNotices(); });
    }
});
