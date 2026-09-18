const searchBox = document.getElementById("searchBox");
const companyFilter = document.getElementById("companyFilter");
const statusFilter = document.getElementById("statusFilter");
const batchFilter = document.getElementById("batchFilter");
const branchFilter = document.getElementById("branchFilter");
const roundFilter = document.getElementById("roundFilter");
const dateFromFilter = document.getElementById("dateFrom");
const dateToFilter = document.getElementById("dateTo");
const clearFiltersBtn = document.getElementById("clearFiltersBtn");
const appsBody = document.getElementById("appsBody");
const emptyMsg = document.getElementById("emptyMsg");
const appStats = document.getElementById("appStats");
const resultCount = document.getElementById("resultCount");
const paginationEl = document.getElementById("pagination");

const statuses = ["IN_PROGRESS", "SELECTED", "REJECTED", "WITHDRAWN",
    "Applied", "Under Review", "Shortlisted", "Interview Scheduled"];
let applications = [];
let page = 1;
const PAGE_SIZE = 20;

function fmtDay(value) {
    return value ? String(value).slice(0, 10) : "-";
}

function statusOf(app) {
    return app.status || "Applied";
}

function optionList(values, label) {
    const esc = window.UI.escapeHtml;
    return '<option value="">' + label + "</option>" +
        [...new Set(values.filter(Boolean))].sort().map(function(value) {
            return '<option value="' + esc(value) + '">' + esc(value) + "</option>";
        }).join("");
}

async function loadApplications() {
    try {
        const response = await fetch("/user/applications", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) return;
        applications = await response.json();
        fillStats();
        fillFilters();
        resetPage();
        render();
    } catch (e) {
        emptyMsg.style.display = "block";
    }
}

function fillStats() {
    let inProgress = 0, applied = 0, underReview = 0, shortlisted = 0;
    let interview = 0, selected = 0, rejected = 0, withdrawn = 0;
    applications.forEach(function(app) {
        switch (String(statusOf(app)).toUpperCase()) {
            case "IN_PROGRESS": inProgress++; break;
            case "APPLIED": applied++; break;
            case "UNDER REVIEW": underReview++; break;
            case "SHORTLISTED": shortlisted++; break;
            case "INTERVIEW SCHEDULED": interview++; break;
            case "SELECTED": selected++; break;
            case "REJECTED": rejected++; break;
            case "WITHDRAWN": withdrawn++; break;
        }
    });
    const cards = [
        ["Total Applications", applications.length],
        ["In Progress", inProgress + applied + underReview],
        ["Shortlisted", shortlisted],
        ["Interview Scheduled", interview],
        ["Selected", selected],
        ["Rejected", rejected],
        ["Withdrawn", withdrawn]
    ];
    appStats.innerHTML = cards.map(function(card) {
        return '<div class="stat-card"><strong>' + card[1] + '</strong><span>' + card[0] + "</span></div>";
    }).join("");
}

function fillFilters() {
    companyFilter.innerHTML = optionList(applications.map(a => a.company), "All companies");
    statusFilter.innerHTML = optionList(statuses.concat(applications.map(a => a.status)), "All statuses");
    batchFilter.innerHTML = optionList(applications.map(a => a.batch), "All batches");
    branchFilter.innerHTML = optionList(applications.map(a => a.branch), "All branches");

    const maxRound = applications.reduce(function(max, app) {
        return Math.max(max, Number(app.totalRounds) || 0, Number(app.currentRound) || 0);
    }, 0);
    let roundOptions = "";
    for (let i = 1; i <= Math.max(maxRound, 4); i++) {
        roundOptions += '<option value="' + i + '">Round ' + i + "</option>";
    }
    roundFilter.innerHTML = '<option value="">All rounds</option>' + roundOptions;
}

function filteredApps() {
    const search = searchBox.value.toLowerCase().trim();
    const company = companyFilter.value;
    const status = statusFilter.value;
    const batch = batchFilter.value;
    const branch = branchFilter.value;
    const round = roundFilter.value;
    const fromMs = dateFromFilter.value ? new Date(dateFromFilter.value + "T00:00:00").getTime() : null;
    const toMs = dateToFilter.value ? new Date(dateToFilter.value + "T23:59:59.999").getTime() : null;

    return applications.filter(function(app) {
        const text = (app.company + " " + app.role + " " + (app.batch || "") + " "
            + (app.branch || "") + " " + (app.location || "")).toLowerCase();
        const appMs = window.UI.parseTimestamp(app.applicationDate);
        const matchesSearch = !search || text.includes(search);
        const matchesCompany = !company || app.company === company;
        const matchesStatus = !status || statusOf(app) === status;
        const matchesBatch = !batch || window.UI.batchMatches(app.batch, batch);
        const matchesBranch = !branch || app.branch === branch;
        const matchesRound = !round || String(app.currentRound) === round;
        const matchesDate = (!fromMs && !toMs) ||
            (appMs !== null &&
                (!fromMs || appMs >= fromMs) &&
                (!toMs || appMs <= toMs));
        return matchesSearch && matchesCompany && matchesStatus && matchesBatch &&
            matchesBranch && matchesRound && matchesDate;
    });
}

function resetPage() {
    page = 1;
}

function goToPage(target) {
    const filtered = filteredApps();
    const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    page = Math.max(1, Math.min(target, pageCount));
    render();
}

function renderPagination(total, pageCount) {
    if (pageCount <= 1) {
        paginationEl.innerHTML = "";
        return;
    }
    let buttons = "";
    buttons += '<button type="button" class="page-btn" data-page="' + (page - 1) + '"'
        + (page <= 1 ? " disabled" : "") + ' aria-label="Previous page">‹</button>';
    for (let p = 1; p <= pageCount; p++) {
        const show = pageCount <= 9 || p === 1 || p === pageCount || (p >= page - 1 && p <= page + 1);
        if (!show) {
            if (p === 2 || p === pageCount - 1) buttons += '<span class="page-ellipsis">…</span>';
            continue;
        }
        buttons += '<button type="button" class="page-btn' + (p === page ? " active" : "")
            + '" data-page="' + p + '">' + p + "</button>";
    }
    buttons += '<button type="button" class="page-btn" data-page="' + (page + 1) + '"'
        + (page >= pageCount ? " disabled" : "") + ' aria-label="Next page">›</button>';
    paginationEl.innerHTML = '<span class="page-info">' + total + " result" + (total === 1 ? "" : "s")
        + " · Page " + page + " of " + pageCount + "</span>"
        + '<span class="page-buttons">' + buttons + "</span>";
}

function rowHtml(app) {
    const esc = window.UI.escapeHtml;
    return '<tr class="app-row" data-id="' + esc(app.applicationId || "") + '">'
        + '<td class="cell-company">'
        + '<div class="job-company">' + esc(app.company || "-") + "</div>"
        + '<div class="job-role">' + esc(app.role || app.jobType || "-") + "</div>"
        + "</td>"
        + '<td><div class="meta-chips"><span class="meta-chip">' + esc(app.batch || "—")
        + '</span><span class="chip-sep">·</span><span class="meta-chip">' + esc(app.branch || "—")
        + "</span></div></td>"
        + '<td><span class="badge ' + window.UI.badge(app.status) + '">' + esc(statusOf(app)) + "</span></td>"
        + '<td class="cell-date">' + esc(fmtDay(app.applicationDate)) + "</td>"
        + '<td class="cell-progress">' + window.UI.roundPills(app) + "</td>"
        + '<td class="cell-action"><button type="button" class="row-expand-btn" data-expand="'
        + esc(app.applicationId || "") + '" aria-label="Toggle details" aria-expanded="false">'
        + '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg>'
        + "</button></td>"
        + "</tr>";
}

function detailRowHtml(app) {
    const esc = window.UI.escapeHtml;
    return '<tr class="expand-row hidden" data-detail-for="' + esc(app.applicationId || "") + '">'
        + '<td colspan="6">'
        + '<div class="expand-grid">'
        + '<div class="expand-section">'
        + '<div class="expand-title">Job Details</div>'
        + '<div class="kv-grid">'
        + kv("Salary / CTC", app.salary)
        + kv("Program Fee", app.programFee)
        + kv("Duration", app.duration)
        + kv("Location", app.location)
        + kv("Openings", app.openings)
        + kv("Batch", app.batch)
        + kv("Branch", app.branch)
        + kv("Eligibility", app.eligibility)
        + kv("Skills", app.skills)
        + kv("Application Deadline", app.applicationDeadline)
        + "</div>"
        + (app.jobDescription ? '<div class="expand-desc">' + esc(app.jobDescription) + "</div>" : "")
        + (app.applicationLink
            ? '<a class="expand-link" href="' + esc(app.applicationLink) + '" target="_blank" rel="noopener">Open application link →</a>'
            : "")
        + "</div>"
        + '<div class="expand-section">'
        + '<div class="expand-title">Application Info</div>'
        + '<div class="kv-grid">'
        + kv("Application ID", app.applicationId)
        + kv("Current Round", "Round " + String(app.currentRound || 1) + " of " + String(app.totalRounds || app.currentRound || 1))
        + kv("Applied on", window.UI.fmtDateTime(app.applicationDate))
        + kv("Last updated", window.UI.fmtDateTime(app.lastUpdated))
        + kv("Admin remark", app.adminRemark)
        + "</div>"
        + '<div class="expand-title" style="margin-top:14px;">Round History</div>'
        + window.UI.progressList(app)
        + "</div>"
        + "</div>"
        + "</td>"
        + "</tr>";
}

function kv(label, value) {
    const esc = window.UI.escapeHtml;
    return '<div class="kv"><span class="kv-label">' + label + '</span><span class="kv-value">'
        + (value === null || value === undefined ? "-" : esc(value)) + "</span></div>";
}

function render() {
    const filtered = filteredApps();
    emptyMsg.style.display = filtered.length === 0 ? "block" : "none";

    const total = filtered.length;
    resultCount.textContent = total + " of " + applications.length
        + (applications.length === 1 ? " application" : " applications");

    const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
    if (page > pageCount) page = pageCount;
    const start = (page - 1) * PAGE_SIZE;
    const slice = filtered.slice(start, start + PAGE_SIZE);

    appsBody.innerHTML = slice.map(function(app) {
        return rowHtml(app) + detailRowHtml(app);
    }).join("");

    renderPagination(total, pageCount);
}

function clearFilters() {
    [searchBox, companyFilter, statusFilter, batchFilter, branchFilter, roundFilter,
        dateFromFilter, dateToFilter].forEach(function(el) {
        if (el.tagName === "INPUT") el.value = "";
        else el.selectedIndex = 0;
    });
    resetPage();
    render();
}

appsBody.addEventListener("click", function(event) {
    const button = event.target.closest(".row-expand-btn");
    if (!button) return;
    const row = button.closest("tr");
    const detail = row && row.nextElementSibling;
    if (!detail || !detail.classList.contains("expand-row")) return;
    const open = detail.classList.toggle("hidden");
    button.classList.toggle("open", !open);
    button.setAttribute("aria-expanded", String(!open));
});

paginationEl.addEventListener("click", function(event) {
    const button = event.target.closest(".page-btn");
    if (!button || button.disabled || !button.dataset.page) return;
    goToPage(Number(button.dataset.page));
});

searchBox.addEventListener("input", function() { resetPage(); render(); });
companyFilter.addEventListener("change", function() { resetPage(); render(); });
statusFilter.addEventListener("change", function() { resetPage(); render(); });
batchFilter.addEventListener("change", function() { resetPage(); render(); });
branchFilter.addEventListener("change", function() { resetPage(); render(); });
roundFilter.addEventListener("change", function() { resetPage(); render(); });
dateFromFilter.addEventListener("change", function() { resetPage(); render(); });
dateToFilter.addEventListener("change", function() { resetPage(); render(); });
clearFiltersBtn.addEventListener("click", clearFilters);

document.addEventListener("DOMContentLoaded", loadApplications);