async function loadStats() {
    try {
        const response = await fetch("/admin/stats", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (response.ok) {
            const stats = await response.json();
            document.getElementById("statApplications").textContent = stats.total || 0;
            document.getElementById("statUnderReview").textContent = stats.underReview || 0;
            document.getElementById("statShortlisted").textContent = stats.shortlisted || 0;
            document.getElementById("statSelected").textContent = stats.selected || 0;
            document.getElementById("statRejected").textContent = stats.rejected || 0;
        }
    } catch (e) {
        // stats load failed silently
    }
}

async function loadPlacements() {
    try {
        const response = await fetch("/admin/jobs", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (response.ok) {
            const jobs = await response.json();
            document.getElementById("statPlacements").textContent = (jobs || []).length;
        }
    } catch (e) {
        // placements load failed silently
    }
}

document.addEventListener("DOMContentLoaded", function() {
    loadStats();
    loadPlacements();
});
