const searchBox = document.getElementById("searchBox");
const jobFilter = document.getElementById("jobFilter");
const companyFilter = document.getElementById("companyFilter");
const statusFilter = document.getElementById("statusFilter");
const roundFilter = document.getElementById("roundFilter");
const batchFilter = document.getElementById("batchFilter");
const branchFilter = document.getElementById("branchFilter");
const dateFromFilter = document.getElementById("dateFrom");
const dateToFilter = document.getElementById("dateTo");
const clearAppFiltersBtn = document.getElementById("clearAppFiltersBtn");
const appResultCount = document.getElementById("appResultCount");
const appPagination = document.getElementById("appPagination");
const selectQualifiedBtn = document.getElementById("selectQualifiedBtn");
const appsBody = document.getElementById("appsBody");
const emptyMsg = document.getElementById("emptyMsg");
const statusModal = document.getElementById("statusModal");
const appDetail = document.getElementById("appDetail");
const statusSelect = document.getElementById("statusSelect");
const remarkInput = document.getElementById("remarkInput");
const saveStatusBtn = document.getElementById("saveStatusBtn");
const cancelStatusBtn = document.getElementById("cancelStatusBtn");

const actionModal = document.getElementById("actionModal");
const actionTitle = document.getElementById("actionTitle");
const actionInfo = document.getElementById("actionInfo");
const actionScore = document.getElementById("actionScore");
const actionRemark = document.getElementById("actionRemark");
const confirmQualifyBtn = document.getElementById("confirmQualifyBtn");
const confirmRejectBtn = document.getElementById("confirmRejectBtn");
const cancelActionBtn = document.getElementById("cancelActionBtn");

const mgmtJobSelect = document.getElementById("mgmtJobSelect");
const mgmtAppSelect = document.getElementById("mgmtAppSelect");
const mgmtPanel = document.getElementById("mgmtPanel");

const roundConfigSection = document.getElementById("roundConfigSection");
const roundConfigTitle = document.getElementById("roundConfigTitle");
const roundConfigBody = document.getElementById("roundConfigBody");
const addRoundBtn = document.getElementById("addRoundBtn");

const roundModal = document.getElementById("roundModal");
const roundModalTitle = document.getElementById("roundModalTitle");
const roundForm = document.getElementById("roundForm");
const roundNumberInput = document.getElementById("roundNumberInput");
const roundNameInput = document.getElementById("roundNameInput");
const roundDescInput = document.getElementById("roundDescInput");
const cancelRoundBtn = document.getElementById("cancelRoundBtn");
const saveRoundBtn = document.getElementById("saveRoundBtn");

const bulkBar = document.getElementById("bulkBar");
const bulkCount = document.getElementById("bulkCount");
const selectAllCheckbox = document.getElementById("selectAllCheckbox");
const bulkAdvanceBtn = document.getElementById("bulkAdvanceBtn");
const bulkSetRoundBtn = document.getElementById("bulkSetRoundBtn");
const bulkRejectBtn = document.getElementById("bulkRejectBtn");
const bulkClearBtn = document.getElementById("bulkClearBtn");

const setRoundModal = document.getElementById("setRoundModal");
const setRoundInput = document.getElementById("setRoundInput");
const setRoundRemark = document.getElementById("setRoundRemark");
const applySetRoundBtn = document.getElementById("applySetRoundBtn");
const cancelSetRoundBtn = document.getElementById("cancelSetRoundBtn");

const userSearch = document.getElementById("userSearch");
const userSearchClearBtn = document.getElementById("userSearchClearBtn");
const userBatchFilter = document.getElementById("userBatchFilter");
const userBranchFilter = document.getElementById("userBranchFilter");
const userStatusFilter = document.getElementById("userStatusFilter");
const userJobFilter = document.getElementById("userJobFilter");
const userDateFrom = document.getElementById("userDateFrom");
const userDateTo = document.getElementById("userDateTo");
const userClearBtn = document.getElementById("userClearBtn");
const userResults = document.getElementById("userResults");
const userPagination = document.getElementById("userPagination");
const userListState = document.getElementById("userListState");
const userLoadingMsg = document.getElementById("userLoadingMsg");
const userErrorMsg = document.getElementById("userErrorMsg");
const userEmptyMsg = document.getElementById("userEmptyMsg");
const userSearchCard = document.getElementById("userSearchCard");

const statuses = ["IN_PROGRESS", "SELECTED", "REJECTED", "WITHDRAWN",
    "Applied", "Under Review", "Shortlisted", "Interview Scheduled"];

let applications = [];
let allUsers = [];
let usersLazyLoaded = false;
let usersLoadPromise = null;
let usersById = {};
let usersByEnrollment = {};
let editingApp = null;
let pendingAction = null;
let selectedIds = new Set();
let actionableIds = [];
let jobsById = {};
let mgmtJobs = [];

let appPage = 1;
const APP_PAGE_SIZE = 25;
let userPage = 1;
const USER_PAGE_SIZE = 20;
let userPageCount = 1;

let jobRoundsCache = {};
const configPromises = {};


async function fetchAllApplicationsPage() {
    const MAX_PAGE_SIZE = 100;
    const accumulator = [];
    let page = 1;
    let total = null;
    for (;;) {
        const url = "/admin/applications?page=" + page + "&pageSize=" + MAX_PAGE_SIZE;
        const response = await fetch(url, { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return accumulator;
        if (!response.ok) break;
        const data = await response.json();
        const list = (data && Array.isArray(data.applications)) ? data.applications : [];
        accumulator.push.apply(accumulator, list);
        total = data && Number.isFinite(data.total) ? data.total : null;
        if (total === null || accumulator.length >= total) break;
        page += 1;
    }
    return accumulator;
}

function unpaginateAllApplicationsPage(data) {
    return (data && Array.isArray(data.applications)) ? data.applications : (Array.isArray(data) ? data : []);
}

async function loadApplications() {
    userLoadingMsg.classList.remove("hidden");
    userErrorMsg.classList.add("hidden");
    userEmptyMsg.classList.add("hidden");
    userListState.textContent = "Loading students…";
    try {
        const appsResponse = await fetch("/admin/applications", { credentials: "include" });
        if (UI.redirectIfUnauthorized(appsResponse)) return;
        applications = await fetchAllApplicationsPage();
        enrichApplicationsWithUsers();
        fillFilters();
        render();
        refreshMgmtApplicants();
        renderUsersLazyHint();
    } catch (e) {
        emptyMsg.style.display = "block";
        userLoadingMsg.classList.add("hidden");
        userEmptyMsg.classList.add("hidden");
        userErrorMsg.classList.remove("hidden");
        userListState.textContent = "Unable to load students";
        userResults.innerHTML = "";
    }
}

function ensureUsersLazy() {
    if (usersLazyLoaded) return Promise.resolve(allUsers);
    if (usersLoadPromise) return usersLoadPromise;
    usersLoadPromise = fetch("/admin/users", { credentials: "include" })
        .then(function(resp) {
            if (UI.redirectIfUnauthorized(resp)) return [];
            return resp.ok ? resp.json() : [];
        })
        .then(function(list) {
            allUsers = Array.isArray(list) ? list : [];
            usersLazyLoaded = true;
            buildUsersIndex();
            return allUsers;
        })
        .catch(function() {
            allUsers = [];
            usersLazyLoaded = true;
            buildUsersIndex();
            return allUsers;
        });
    return usersLoadPromise;
}

function renderUsersLazyHint() {
    userListState.textContent = "Select a filter or search to load students…";
    userResults.innerHTML = "";
    userLoadingMsg.classList.add("hidden");
    userErrorMsg.classList.add("hidden");
    userEmptyMsg.classList.remove("hidden");
}

function buildUsersIndex() {
    usersById = {};
    usersByEnrollment = {};
    (Array.isArray(allUsers) ? allUsers : []).forEach(function(user) {
        if (user && user.id) {
            usersById[user.id] = user;
            if (user.enrollmentno && String(user.enrollmentno).trim()) {
                usersByEnrollment[normalizeKey(user.enrollmentno)] = user;
            }
        }
    });
}

function resolveUserForApplication(app) {
    if (!app) return null;
    let user;
    if (app.studentId && usersById[app.studentId]) {
        user = usersById[app.studentId];
    }
    if (!user && app.studentId) {
        user = usersById[normalizeKey(app.studentId)] || usersByEnrollment[normalizeKey(app.studentId)];
    }
    if (!user && app.enrollmentno) {
        user = usersByEnrollment[normalizeKey(app.enrollmentno)];
    }
    return user || null;
}

function enrichApplicationsWithUsers() {
    applications.forEach(function(app) {
        const user = resolveUserForApplication(app);
        if (!user) return;
        const name = firstNonEmpty(user.name);
        const email = firstNonEmpty(user.email);
        const enrollment = firstNonEmpty(user.enrollmentno);
        const phone = firstNonEmpty(user.phoneno);
        if (name) app.studentName = name;
        if (email) app.studentEmail = email;
        if (enrollment) app.enrollmentno = enrollment;
        if (phone) app.phoneNo = phone;
        if (!app.studentId && user.id) app.studentId = user.id;
    });
}

function normalizeKey(value) {
    return value === null || value === undefined ? "" : String(value).trim().toLowerCase();
}

function fillFilters() {
    const esc = window.UI.escapeHtml;
    const companies = [...new Set(applications.map(a => a.company).filter(Boolean))].sort();
    companyFilter.innerHTML = '<option value="">All companies</option>' +
        companies.map(c => '<option value="' + esc(c) + '">' + esc(c) + "</option>").join("");
    const presentStatuses = [...new Set(statuses.concat(applications.map(a => a.status).filter(Boolean)))];
    statusFilter.innerHTML = '<option value="">All statuses</option>' +
        presentStatuses.map(s => '<option value="' + esc(s) + '">' + esc(s) + "</option>").join("");
    const batches = [...new Set(applications.map(a => a.batch).filter(Boolean))].sort();
    batchFilter.innerHTML = '<option value="">All batches</option>' +
        batches.map(b => '<option value="' + esc(b) + '">' + esc(b) + "</option>").join("");
    const branches = [...new Set(applications.map(a => a.branch).filter(Boolean))].sort();
    branchFilter.innerHTML = '<option value="">All branches</option>' +
        branches.map(b => '<option value="' + esc(b) + '">' + esc(b) + "</option>").join("");

    userBatchFilter.innerHTML = '<option value="">All batches</option>' +
        batches.map(b => '<option value="' + esc(b) + '">' + esc(b) + "</option>").join("");
    userBranchFilter.innerHTML = '<option value="">All branches</option>' +
        branches.map(b => '<option value="' + esc(b) + '">' + esc(b) + "</option>").join("");
    userStatusFilter.innerHTML = '<option value="">All application statuses</option>' +
        presentStatuses.map(s => '<option value="' + esc(s) + '">' + esc(s) + "</option>").join("");
}

function fillJobFilters() {
    const esc = window.UI.escapeHtml;
    const options = mgmtJobs.map(function(job) {
        const label = [job.company, job.jobType].filter(Boolean).join(" — ");
        return '<option value="' + esc(job.id || "") + '">' + esc(label || "Untitled job") + "</option>";
    }).join("");
    jobsById = {};
    mgmtJobs.forEach(function(job) { jobsById[job.id] = job; });
    jobFilter.innerHTML = '<option value="">All Jobs</option>' + options;
    mgmtJobSelect.innerHTML = '<option value="">Select a Job…</option>' + options;
    userJobFilter.innerHTML = '<option value="">All jobs</option>' + options;
}

function ensureConfig(jobId) {
    if (!jobId) return Promise.resolve([]);
    if (configPromises[jobId]) return configPromises[jobId];
    configPromises[jobId] = fetch("/admin/jobs/" + jobId + "/rounds", { credentials: "include" })
        .then(function(response) {
            if (UI.redirectIfUnauthorized(response)) return [];
            return response.ok ? response.json() : [];
        })
        .then(function(list) {
            jobRoundsCache[jobId] = Array.isArray(list) ? list : [];
            render();
            syncSelectionUi();
            return jobRoundsCache[jobId];
        })
        .catch(function() { jobRoundsCache[jobId] = []; return []; });
    return configPromises[jobId];
}

function reloadConfig(jobId) {
    delete configPromises[jobId];
    return ensureConfig(jobId);
}

function configuredNumbers(jobId) {
    const cached = jobRoundsCache[jobId];
    let numbers;
    if (cached && cached.length) {
        numbers = cached.map(function(r) { return Number(r.roundNumber); })
            .filter(function(n) { return Number.isFinite(n) && n >= 1; });
    } else {
        const job = jobsById[jobId];
        numbers = (((job && Array.isArray(job.rounds)) ? job.rounds : []) || [])
            .map(function(entry) { return entry ? Number(entry.round) : NaN; })
            .filter(function(n) { return Number.isFinite(n) && n > 0; });
    }
    const unique = [...new Set(numbers)].sort(function(a, b) { return a - b; });
    return unique.length ? unique : [1];
}

function nextConfigured(jobId, currentRound) {
    const current = Number(currentRound) || 1;
    const found = configuredNumbers(jobId).find(function(number) { return number > current; });
    return found == null ? null : found;
}

function configuredName(jobId, roundNumber) {
    const cached = jobRoundsCache[jobId];
    if (cached && cached.length) {
        const hit = cached.find(function(r) { return Number(r.roundNumber) === Number(roundNumber); });
        if (hit && hit.roundName) return hit.roundName;
        return null;
    }
    const job = jobsById[jobId];
    const embedded = ((job && Array.isArray(job.rounds)) ? job.rounds : []).find(function(entry) {
        return entry && Number(entry.round) === Number(roundNumber) && entry.name;
    });
    return embedded ? embedded.name : null;
}

function roundLabel(jobId, roundNumber) {
    const name = configuredName(jobId, roundNumber);
    return name ? "Round " + roundNumber + " — " + name : "Round " + roundNumber;
}

function rebuildRoundFilter() {
    const jobId = jobFilter.value;
    if (!jobId) {
        roundFilter.innerHTML = '<option value="">All Rounds</option>';
        roundFilter.disabled = true;
        resetAppPage();
        render();
        return Promise.resolve();
    }
    roundFilter.disabled = false;
    return ensureConfig(jobId).then(function() {
        const numbers = configuredNumbers(jobId);
        const previous = roundFilter.value;
        roundFilter.innerHTML = '<option value="">All Rounds</option>' + numbers.map(function(number) {
            return '<option value="' + number + '">' + window.UI.escapeHtml(roundLabel(jobId, number)) + "</option>";
        }).join("");
        if (previous && numbers.includes(Number(previous))) roundFilter.value = previous;
        resetAppPage();
        render();
    });
}

function isTerminal(app) {
    return ["SELECTED", "REJECTED", "WITHDRAWN"].includes(String(app.status).toUpperCase());
}

function jobIdOf(app) {
    return app.placementId || app.jobId || "";
}

function isCurrentRoundPending(app) {
    if (isTerminal(app)) return false;
    const current = Number(app.currentRound) || 1;
    const entry = (app.rounds || []).find(function(item) { return Number(item.round) === current; });
    return !entry || String(entry.status || "PENDING").toUpperCase() === "PENDING";
}

function latestScore(app) {
    const evaluated = (app.rounds || [])
        .filter(entry => entry && entry.score != null)
        .sort((a, b) => (a.round || 0) - (b.round || 0));
    return evaluated.length ? evaluated[evaluated.length - 1].score : null;
}

function formatDate(value) {
    return window.UI.fmtDateTime(value);
}

function inDateRange(value, fromMs, toMs) {
    if (!fromMs && !toMs) return true;
    const ms = window.UI.parseTimestamp(value);
    if (ms === null) return false;
    return (!fromMs || ms >= fromMs) && (!toMs || ms <= toMs);
}

function actionsMenuHtml(app) {
    const esc = window.UI.escapeHtml;
    const evaluable = isCurrentRoundPending(app);
    const term = isTerminal(app);
    const id = esc(app.applicationId);
    const disabled = evaluable ? "" : " disabled";
    const rejectDisabled = term ? " disabled" : "";
    return '<div class="menu-wrap">'
        + '<button type="button" class="menu-btn" data-menu="' + id + '" aria-label="Actions">⋮</button>'
        + '<div class="row-menu hidden" data-menu-for="' + id + '">'
        + '<button type="button" class="menu-item view-btn" data-id="' + id + '">View Details</button>'
        + '<button type="button" class="menu-item qualify-btn" data-id="' + id + '"' + disabled + '>Evaluate Round</button>'
        + '<button type="button" class="menu-item advance-btn" data-id="' + id + '"' + disabled + '>Send to Next Round</button>'
        + '<button type="button" class="menu-item reject-btn" data-id="' + id + '"' + rejectDisabled + '>Reject</button>'
        + '<button type="button" class="menu-item status-btn" data-id="' + id + '">Update Status</button>'
        + "</div></div>";
}

function closeOpenMenus() {
    document.querySelectorAll(".row-menu").forEach(function(menu) { menu.classList.add("hidden"); });
}

function filterApplications() {
    const search = searchBox.value.toLowerCase().trim();
    const job = jobFilter.value;
    const company = companyFilter.value;
    const status = statusFilter.value;
    const round = roundFilter.value;
    const batch = batchFilter.value;
    const branch = branchFilter.value;
    const fromMs = dateFromFilter.value ? new Date(dateFromFilter.value + "T00:00:00").getTime() : null;
    const toMs = dateToFilter.value ? new Date(dateToFilter.value + "T23:59:59.999").getTime() : null;

    return applications.filter(function(app) {
        const text = (app.studentName + " " + app.studentEmail + " " + app.company + " "
            + app.role + " " + (app.effectiveRole || "") + " " + (app.selectedCourse || "") + " "
            + app.applicationId + " " + (app.enrollmentno || "")).toLowerCase();
        const matchesSearch = !search || text.includes(search);
        const matchesJob = !job || jobIdOf(app) === job;
        const matchesCompany = !company || app.company === company;
        const matchesStatus = !status || app.status === status;
        const matchesRound = !round || String(app.currentRound) === round;
        const matchesBatch = !batch || window.UI.batchMatches(app.batch, batch);
        const matchesBranch = !branch || app.branch === branch;
        const matchesDate = inDateRange(app.applicationDate, fromMs, toMs);
        return matchesSearch && matchesJob && matchesCompany && matchesStatus && matchesRound
            && matchesBatch && matchesBranch && matchesDate;
    });
}

function render() {
    const filtered = filterApplications();

    emptyMsg.style.display = filtered.length === 0 ? "block" : "none";
    filtered.forEach(function(app) { ensureConfig(jobIdOf(app)); });
    actionableIds = filtered.filter(function(app) { return !isTerminal(app); })
        .map(function(app) { return app.applicationId; });

    const total = filtered.length;
    appResultCount.textContent = total + " of " + applications.length
        + (applications.length === 1 ? " application" : " applications");
    const pageCount = Math.max(1, Math.ceil(total / APP_PAGE_SIZE));
    if (appPage > pageCount) appPage = pageCount;
    const start = (appPage - 1) * APP_PAGE_SIZE;
    const slice = filtered.slice(start, start + APP_PAGE_SIZE);

    appsBody.innerHTML = slice.map(function(app) {
        const esc = window.UI.escapeHtml;
        const ended = isTerminal(app);
        const checked = selectedIds.has(app.applicationId);
        const checkbox = ended
            ? '<td class="select-cell"></td>'
            : '<td class="select-cell"><input type="checkbox" class="row-select" data-id="'
                + esc(app.applicationId) + '"' + (checked ? " checked" : "") + "></td>";
        const score = latestScore(app);
        return `
            <tr>
                ${checkbox}
                <td>${esc(app.studentName || "-")}</td>
                <td>${esc(app.studentEmail || "-")}</td>
                <td>${esc(app.company || "-")}</td>
                <td>${esc(app.role || "-")}</td>
                <td>${esc(app.effectiveRole || app.selectedRole || app.role || "-")}</td>
                <td>${esc(app.selectedCourse || app.studentCourse || "-")}</td>
                <td><div class="meta-chips"><span class="meta-chip">${esc(app.batch || "—")}</span><span class="chip-sep">·</span><span class="meta-chip">${esc(app.branch || "—")}</span></div></td>
                <td>${esc(formatDate(app.applicationDate))}</td>
                <td>${window.UI.roundPills(app)}</td>
                <td>${score != null ? esc(score) : "-"}</td>
                <td><span class="badge ${window.UI.badge(app.status)}">${esc(app.status || "Applied")}</span></td>
                <td>${actionsMenuHtml(app)}</td>
            </tr>
        `;
    }).join("");

    syncSelectionUi();
    renderAppPagination(total, pageCount);
}

function resetAppPage() {
    appPage = 1;
}

function goAppPage(target) {
    const filtered = filterApplications();
    const pageCount = Math.max(1, Math.ceil(filtered.length / APP_PAGE_SIZE));
    appPage = Math.max(1, Math.min(target, pageCount));
    render();
}

function renderAppPagination(total, pageCount) {
    if (pageCount <= 1) {
        appPagination.innerHTML = "";
        return;
    }
    let buttons = "";
    buttons += '<button type="button" class="page-btn" data-apage="' + (appPage - 1) + '"'
        + (appPage <= 1 ? " disabled" : "") + ' aria-label="Previous page">‹</button>';
    for (let p = 1; p <= pageCount; p++) {
        const show = pageCount <= 11 || p === 1 || p === pageCount || (p >= appPage - 1 && p <= appPage + 1);
        if (!show) {
            if (p === 2 || p === pageCount - 1) buttons += '<span class="page-ellipsis">…</span>';
            continue;
        }
        buttons += '<button type="button" class="page-btn' + (p === appPage ? " active" : "")
            + '" data-apage="' + p + '">' + p + "</button>";
    }
    buttons += '<button type="button" class="page-btn" data-apage="' + (appPage + 1) + '"'
        + (appPage >= pageCount ? " disabled" : "") + ' aria-label="Next page">›</button>';
    appPagination.innerHTML = '<span class="page-info">' + total + " application" + (total === 1 ? "" : "s")
        + " · Page " + appPage + " of " + pageCount + "</span>"
        + '<span class="page-buttons">' + buttons + "</span>";
}

function firstNonEmpty() {
    for (let i = 0; i < arguments.length; i++) {
        const value = arguments[i];
        if (value !== null && value !== undefined && String(value).trim() !== "") return String(value).trim();
    }
    return "";
}

function groupUsers() {
    const map = new Map();
    applications.forEach(function(app) {
        const key = app.studentId || app.enrollmentno || app.studentEmail || app.applicationId;
        if (!key) return;
        let user = map.get(key);
        if (!user) {
            user = {
                key: key,
                studentId: app.studentId || "",
                studentName: "",
                studentEmail: "",
                enrollmentno: "",
                phoneNo: "",
                studentIdSet: new Set(),
                apps: [],
                batches: new Set(),
                branches: new Set(),
                statuses: new Set()
            };
            map.set(key, user);
        }
        if (app.studentId) user.studentIdSet.add(app.studentId);
        if (app.studentName) user.studentName = user.studentName || app.studentName;
        if (app.studentEmail) user.studentEmail = user.studentEmail || app.studentEmail;
        if (app.enrollmentno) user.enrollmentno = user.enrollmentno || app.enrollmentno;
        if (app.phoneNo) user.phoneNo = user.phoneNo || app.phoneNo;
        user.apps.push(app);
        if (app.batch) user.batches.add(app.batch);
        if (app.branch) user.branches.add(app.branch);
        if (app.status) user.statuses.add(app.status);
    });

    return Array.from(map.values()).map(function(user) {
        user.studentName = firstNonEmpty(user.studentName, user.enrollmentno, user.studentEmail);
        user.studentEmail = firstNonEmpty(user.studentEmail);
        user.enrollmentno = firstNonEmpty(user.enrollmentno);
        user.phoneNo = firstNonEmpty(user.phoneNo);
        return user;
    });
}

function filterUsers() {
    const keyword = userSearch.value.toLowerCase().trim();
    const batch = userBatchFilter.value;
    const branch = userBranchFilter.value;
    const status = userStatusFilter.value;
    const fromMs = userDateFrom.value ? new Date(userDateFrom.value + "T00:00:00").getTime() : null;
    const toMs = userDateTo.value ? new Date(userDateTo.value + "T23:59:59.999").getTime() : null;

    return groupUsers().filter(function(user) {
        const jobFilterVal = userJobFilter ? userJobFilter.value : "";
        if (jobFilterVal) {
            const hasJob = user.apps.some(function(app) { return jobIdOf(app) === jobFilterVal; });
            if (!hasJob) return false;
        }
        if (keyword) {
            const haystack = (user.studentName + " " + user.studentEmail + " " + user.enrollmentno
                + " " + user.phoneNo + " " + user.studentId + " " + user.apps.map(function(app) {
                    return (app.company || "") + " " + (app.role || "") + " " + (app.applicationId || "");
                }).join(" ")).toLowerCase();
            if (!haystack.includes(keyword)) return false;
        }
        if (batch && !Array.from(user.batches).some(function(b) { return window.UI.batchMatches(b, batch); })) return false;
        if (branch && !user.branches.has(branch)) return false;
        if (status && !Array.from(user.statuses).some(function(s) {
            return String(s).toLowerCase() === String(status).toLowerCase();
        })) return false;
        if (fromMs || toMs) {
            const anyInRange = user.apps.some(function(app) {
                return inDateRange(app.applicationDate, fromMs, toMs);
            });
            if (!anyInRange) return false;
        }
        return true;
    });
}

function statusDisplay(status) {
    const value = String(status || "Applied").trim();
    switch (value.toUpperCase()) {
        case "IN_PROGRESS": return "In Progress";
        case "SELECTED": return "Selected";
        case "REJECTED": return "Rejected";
        case "WITHDRAWN": return "Withdrawn";
        default: return value || "Applied";
    }
}

function userStatusSummary(user) {
    const counts = {};
    user.apps.forEach(function(app) {
        const st = app.status || "Applied";
        counts[st] = (counts[st] || 0) + 1;
    });
    return Object.keys(counts).sort().map(function(st) {
        return '<span class="badge ' + window.UI.badge(st) + '" title="' + counts[st]
            + " application" + (counts[st] === 1 ? "" : "s") + " with status "
            + window.UI.escapeHtml(statusDisplay(st)) + '">'
            + window.UI.escapeHtml(statusDisplay(st)) + " <strong>" + counts[st] + "</strong></span>";
    }).join("");
}

function fmtDateTimeNice(value) {
    const ms = window.UI.parseTimestamp(value);
    if (ms === null) return "-";
    const d = new Date(ms);
    let hours = d.getHours();
    const ampm = hours >= 12 ? "PM" : "AM";
    hours = hours % 12 || 12;
    const minutes = String(d.getMinutes()).padStart(2, "0");
    return String(d.getDate()).padStart(2, "0") + "-" + String(d.getMonth() + 1).padStart(2, "0")
        + "-" + d.getFullYear() + ", " + hours + ":" + minutes + " " + ampm;
}

function roundLadderHtml(jobId, app) {
    const byNumber = {};
    (app.rounds || []).forEach(function(entry) {
        if (entry && entry.round != null) byNumber[entry.round] = entry;
    });
    const total = Math.max(Number(app.totalRounds) || 1,
        Number(app.currentRound) || 1, Object.keys(byNumber).length || 1);
    const currentRound = Number(app.currentRound) || 1;
    const esc = window.UI.escapeHtml;
    let steps = "";
    for (let i = 1; i <= total; i++) {
        const entry = byNumber[i];
        let status = entry ? String(entry.status || "PENDING").toUpperCase() : "UPCOMING";
        const isCurrent = i === currentRound && status !== "REJECTED" && status !== "QUALIFIED";
        let cls = status.toLowerCase();
        if (isCurrent) cls += " current";
        let dot;
        if (status === "QUALIFIED") dot = "✓";
        else if (status === "REJECTED") dot = "✕";
        else if (isCurrent) dot = "●";
        else if (status === "PENDING") dot = "●";
        else dot = "○";
        let label = roundLabel(jobId, i);
        if (entry && entry.name) label = "Round " + i + " — " + entry.name;
        steps += '<div class="rl-step ' + cls + '" role="listitem">'
            + '<span class="rl-dot" aria-hidden="true">' + dot + "</span>"
            + '<span class="rl-label">' + esc(label) + "</span>"
            + (isCurrent ? '<span class="rl-tag">Current</span>' : "")
            + "</div>";
    }
    return '<div class="round-ladder" role="list" aria-label="Application round progress">'
        + steps + "</div>";
}

function currentRoundLabel(app) {
    const roundNumber = Number(app.currentRound) || 1;
    const entry = (app.rounds || []).find(function(item) { return Number(item.round) === roundNumber; });
    if (entry && entry.name) return "Round " + roundNumber + " — " + entry.name;
    return roundLabel(jobIdOf(app), roundNumber);
}

function detailIdForUser(user) {
    return "usr-" + String(user.key).replace(/[^A-Za-z0-9_-]/g, "_");
}

function userRowHtml(user) {
    const esc = window.UI.escapeHtml;
    const detailId = detailIdForUser(user);
    const name = user.studentName || "Name unavailable";
    const email = user.studentEmail || "Email unavailable";
    const enrollment = user.enrollmentno ? "Enrollment: " + user.enrollmentno : "Enrollment unavailable";
    const batchChips = Array.from(user.batches).length
        ? '<span class="meta-chip">Batch: ' + esc(Array.from(user.batches).join(", ")) + "</span>" : "";
    const branchChips = Array.from(user.branches).length
        ? '<span class="meta-chip">Branch: ' + esc(Array.from(user.branches).join(", ")) + "</span>" : "";
    const appCountLabel = user.apps.length + " application" + (user.apps.length === 1 ? "" : "s");
    const hasFallbackName = !user.studentName;
    return '<div class="user-row" data-key="' + esc(user.key) + '" data-appcount="' + user.apps.length + '">'
        + '<div class="user-row-head">'
        + '<div class="user-id-block">'
        + '<span class="user-avatar" aria-hidden="true">' + esc(window.UI.initials(user.studentName || user.enrollmentno)) + "</span>"
        + '<div class="user-id-copy">'
        + '<div class="user-row-name' + (hasFallbackName ? " unavailable" : "") + '">' + esc(name) + "</div>"
        + '<div class="user-email">' + esc(email) + "</div>"
        + '<div class="user-enrollment">' + esc(enrollment) + "</div>"
        + "</div>"
        + "</div>"
        + '<div class="user-row-chips">'
        + '<span class="meta-chip muted">Apps: ' + user.apps.length + "</span>"
        + batchChips + branchChips
        + "</div>"
        + '<div class="user-row-status">' + userStatusSummary(user) + "</div>"
        + '<button type="button" class="user-expand user-expand-btn" data-user-expand="' + esc(user.key)
        + '" aria-expanded="false" aria-controls="' + esc(detailId) + '">'
        + '<span class="user-expand-text">' + (user.apps.length === 1 ? "View 1 application" : "View " + user.apps.length + " applications") + "</span>"
        + '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>'
        + "</button>"
        + "</div>"
        + '<div class="user-row-detail hidden" id="' + esc(detailId) + '">' + userDetailHtml(user) + "</div>"
        + "</div>";
}

function userDetailHtml(user) {
    const esc = window.UI.escapeHtml;
    const identityText = esc([user.studentName, user.enrollmentno ? "Enrollment: " + user.enrollmentno : ""]
        .filter(Boolean).join(" · "));
    const sorted = user.apps.slice().sort(function(a, b) {
        return window.UI.parseTimestamp(a.applicationDate) - window.UI.parseTimestamp(b.applicationDate);
    });
    const appRows = sorted.map(function(app) {
        const jobId = jobIdOf(app);
        const currentRound = Number(app.currentRound) || 1;
        const total = Number(app.totalRounds) || currentRound;
        const score = latestScore(app);
        return '<div class="user-app" data-app="' + esc(app.applicationId) + '">'
            + '<div class="user-app-head">'
            + '<div class="user-app-title">' + esc(app.company || "-") + '<span class="user-app-role">'
            + esc(app.role || "") + "</span></div>"
            + '<span class="badge ' + window.UI.badge(app.status) + '">'
            + esc(statusDisplay(app.status)) + "</span>"
            + "</div>"
            + '<div class="meta-chips user-app-chips">'
            + '<span class="meta-chip">Batch: ' + esc(app.batch || "—") + "</span>"
            + '<span class="meta-chip">Branch: ' + esc(app.branch || "—") + "</span>"
            + '<span class="meta-chip">Applied ' + esc(fmtDateTimeNice(app.applicationDate)) + "</span>"
            + "</div>"
            + '<div class="user-app-meta">'
            + '<span><strong>Current round:</strong> ' + esc(currentRoundLabel(app))
            + " (" + currentRound + " of " + total + ")</span>"
            + (score != null ? '<span><strong>Score:</strong> ' + esc(score) + "</span>" : "")
            + (app.adminRemark ? '<span class="user-app-remark" title="Admin remark">'
                + esc(app.adminRemark) + "</span>" : "")
            + "</div>"
            + roundLadderHtml(jobId, app)
            + '<div class="user-app-actions">'
            + '<button type="button" class="btn btn-small btn-secondary act-view" data-id="'
            + esc(app.applicationId) + '">View Details</button>'
            + '<button type="button" class="btn btn-small act-manage" data-id="'
            + esc(app.applicationId) + '">Manage Round</button>'
            + "</div>"
            + "</div>";
    }).join("");

    return '<div class="user-detail-head">'
        + '<div class="user-detail-ident">' + identityText + "</div>"
        + '<div class="meta-chips">'
        + '<span class="meta-chip muted">Batch: ' + esc(Array.from(user.batches).join(", ") || "—") + "</span>"
        + '<span class="meta-chip muted">Branch: ' + esc(Array.from(user.branches).join(", ") || "—") + "</span>"
        + (user.phoneNo ? '<span class="meta-chip muted">Phone: ' + esc(user.phoneNo) + "</span>" : "")
        + (user.studentId ? '<span class="meta-chip muted">Student ID</span>' : "")
        + "</div>"
        + "</div>"
        + '<div class="expand-title">Applications (' + user.apps.length + ")</div>"
        + '<div class="user-apps">' + appRows + "</div>";
}

function goUserPage(target) {
    const users = filterUsers();
    userPageCount = Math.max(1, Math.ceil(users.length / USER_PAGE_SIZE));
    userPage = Math.max(1, Math.min(target, userPageCount));
    renderUsers();
}

function renderUserPagination(total) {
    if (userPageCount <= 1) {
        userPagination.innerHTML = "";
        return;
    }
    let buttons = "";
    buttons += '<button type="button" class="page-btn" data-upage="' + (userPage - 1) + '"'
        + (userPage <= 1 ? " disabled" : "") + ' aria-label="Previous page">‹</button>';
    for (let p = 1; p <= userPageCount; p++) {
        const show = userPageCount <= 11 || p === 1 || p === userPageCount || (p >= userPage - 1 && p <= userPage + 1);
        if (!show) {
            if (p === 2 || p === userPageCount - 1) buttons += '<span class="page-ellipsis">…</span>';
            continue;
        }
        buttons += '<button type="button" class="page-btn' + (p === userPage ? " active" : "")
            + '" data-upage="' + p + '">' + p + "</button>";
    }
    buttons += '<button type="button" class="page-btn" data-upage="' + (userPage + 1) + '"'
        + (userPage >= userPageCount ? " disabled" : "") + ' aria-label="Next page">›</button>';
    userPagination.innerHTML = '<span class="page-info">' + total + " user" + (total === 1 ? "" : "s")
        + " · Page " + userPage + " of " + userPageCount + "</span>"
        + '<span class="page-buttons">' + buttons + "</span>";
}

function renderUsers() {
    userLoadingMsg.classList.add("hidden");
    userErrorMsg.classList.add("hidden");

    const users = filterUsers();
    const total = users.length;
    userEmptyMsg.classList.toggle("hidden", total !== 0);

    let stateText;
    const appCount = users.reduce(function(sum, user) { return sum + user.apps.length; }, 0);
    const keyword = userSearch.value.trim();
    if (keyword) {
        stateText = "Search results: " + total + " student" + (total === 1 ? "" : "s")
            + " • " + appCount + " application" + (appCount === 1 ? "" : "s");
    } else {
        stateText = "Showing " + total + " student" + (total === 1 ? "" : "s")
            + " • " + appCount + " application" + (appCount === 1 ? "" : "s");
    }
    userListState.textContent = stateText;

    userPageCount = Math.max(1, Math.ceil(total / USER_PAGE_SIZE));
    if (userPage > userPageCount) userPage = userPageCount;
    const start = (userPage - 1) * USER_PAGE_SIZE;
    const slice = users.slice(start, start + USER_PAGE_SIZE);

    userResults.innerHTML = slice.map(userRowHtml).join("");
    renderUserPagination(total);
}

function resetUserPage() {
    userPage = 1;
}

function clearAppFilters() {
    [searchBox, jobFilter, companyFilter, statusFilter, roundFilter, batchFilter,
        branchFilter, dateFromFilter, dateToFilter].forEach(function(el) {
        if (el.tagName === "SELECT") el.selectedIndex = 0;
        else el.value = "";
    });
    resetAppPage();
    render();
}

function clearUserFilters() {
    [userSearch, userBatchFilter, userBranchFilter, userStatusFilter, userJobFilter,
        userDateFrom, userDateTo].forEach(function(el) {
        if (el.tagName === "SELECT") el.selectedIndex = 0;
        else el.value = "";
    });
    updateUserSearchClear();
    resetUserPage();
    renderUsers();
}

function updateUserSearchClear() {
    if (!userSearchClearBtn) return;
    userSearchClearBtn.classList.toggle("hidden", !userSearch.value);
}

function clearUserSearch() {
    userSearch.value = "";
    updateUserSearchClear();
    resetUserPage();
    renderUsers();
    userSearch.focus();
}

function selectedActionableApps() {
    return applications.filter(function(app) {
        return selectedIds.has(app.applicationId) && !isTerminal(app);
    });
}

function syncSelectionUi() {
    const apps = selectedActionableApps();
    const count = selectedIds.size;
    bulkBar.classList.toggle("hidden", count === 0);
    bulkCount.textContent = count + " candidate" + (count === 1 ? "" : "s") + " selected";

    let label = null;
    for (const app of apps) {
        const next = nextConfigured(jobIdOf(app), app.currentRound);
        if (next != null) { label = roundLabel(jobIdOf(app), next); break; }
    }
    if (!apps.length) {
        bulkAdvanceBtn.textContent = "Send to Next Round";
    } else if (label) {
        bulkAdvanceBtn.textContent = "Send " + apps.length + " Candidate"
            + (apps.length === 1 ? "" : "s") + " to " + label + " →";
    } else {
        bulkAdvanceBtn.textContent = "Qualify Final Round & Select";
    }
    bulkRejectBtn.textContent = apps.length ? "Reject " + apps.length : "Reject";
    bulkAdvanceBtn.disabled = apps.length === 0;
    bulkRejectBtn.disabled = apps.length === 0;
    bulkSetRoundBtn.disabled = apps.length === 0;

    selectAllCheckbox.checked = actionableIds.length > 0
        && actionableIds.every(function(id) { return selectedIds.has(id); });
}

function clearSelection() {
    selectedIds.clear();
    document.querySelectorAll(".row-select").forEach(function(box) { box.checked = false; });
    syncSelectionUi();
}

async function runBulkAction(mode) {
    const ids = Array.from(selectedIds);
    if (ids.length === 0) return;
    bulkAdvanceBtn.disabled = true;
    bulkRejectBtn.disabled = true;

    let summary = null;
    try {
        const response = await fetch("/admin/applications/bulk/" + mode, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ applicationIds: ids })
        });
        if (UI.redirectIfUnauthorized(response)) return;
        if (response.ok) {
            summary = await response.json();
        } else {
            const error = await response.json().catch(function() { return {}; });
            window.UI.showToast(error.message || "Bulk update failed.", "error");
            return;
        }
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
        return;
    }

    (summary.results || []).forEach(function(result) {
        if (result.application) storeApplication(result.application);
    });
    render();
    clearSelection();

    const moved = Number(summary.moved) || 0;
    const failed = Number(summary.failed) || 0;
    const verb = mode === "advance" ? "moved" : "rejected";
    let message;
    if (moved === 0) {
        message = "No candidates could be " + verb + ".";
    } else {
        const firstMoved = (summary.results || []).find(function(result) {
            return result.status === "MOVED" || result.status === "REJECTED";
        });
        let target = "";
        if (mode === "advance" && firstMoved && firstMoved.application) {
            const app = firstMoved.application;
            target = " to " + roundLabel(jobIdOf(app), firstMoved.toRound);
        }
        message = moved + " candidate" + (moved === 1 ? "" : "s") + " " + verb + target + ".";
    }
    if (failed > 0) {
        message += " " + failed + " could not be processed (already evaluated or finished).";
    }
    window.UI.showToast(message, failed === 0 ? "success" : (moved > 0 ? "success" : "warning"));
}

function openActionModal(mode, app) {
    pendingAction = { mode: mode, app: app };
    const roundNumber = Number(app.currentRound) || 1;
    const roundText = roundLabel(jobIdOf(app), roundNumber);
    actionTitle.textContent = (mode === "qualify" ? "Evaluate " : "Reject ") + roundText;
    const entry = (app.rounds || []).find(function(item) { return Number(item.round) === roundNumber; });
    actionInfo.innerHTML =
        '<p><strong>Student</strong>' + window.UI.escapeHtml(app.studentName || "-")
            + " (" + window.UI.escapeHtml(app.enrollmentno || "") + ")</p>"
        + '<p><strong>Job</strong>' + window.UI.escapeHtml(app.company || "-") + " – "
            + window.UI.escapeHtml(app.role || "-") + "</p>"
        + '<p><strong>Evaluating</strong>' + window.UI.escapeHtml(roundText) + "</p>"
        + '<p><strong>Status</strong><span class="badge ' + window.UI.badge(app.status) + '">'
            + window.UI.escapeHtml(entry ? (entry.status || "PENDING") : "PENDING") + "</span></p>"
        + window.UI.progressList(app);
    actionScore.value = entry && entry.score != null ? entry.score : "";
    actionRemark.value = entry && entry.adminRemark ? entry.adminRemark : "";
    confirmQualifyBtn.style.display = mode === "qualify" ? "inline-flex" : "none";
    confirmRejectBtn.style.display = mode === "reject" ? "inline-flex" : "none";
    window.UI.openModal(actionModal);
}

function closeActionModal() {
    pendingAction = null;
    window.UI.closeModal(actionModal);
}

function buildEvaluationBody() {
    const body = { adminRemark: actionRemark.value.trim() };
    const scoreRaw = actionScore.value.trim();
    if (scoreRaw !== "") {
        const parsed = Number(scoreRaw);
        if (Number.isNaN(parsed)) {
            window.UI.showToast("Score must be a number.", "error");
            return null;
        }
        body.score = parsed;
    }
    return body;
}

async function postEvaluation(url, body, buttons, loadingText) {
    const activeBtn = buttons[0];
    const otherButtons = buttons.slice(1);
    window.UI.setButtonLoading(activeBtn, true, loadingText);
    otherButtons.forEach(function(btn) { btn.disabled = true; });
    try {
        const response = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify(body)
        });
        if (UI.redirectIfUnauthorized(response)) return null;
        if (response.ok) return await response.json();
        const error = await response.json().catch(function() { return {}; });
        window.UI.showToast(error.message || "Unable to update application.", "error");
        return null;
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
        return null;
    } finally {
        window.UI.setButtonLoading(activeBtn, false);
        otherButtons.forEach(function(btn) { btn.disabled = false; });
    }
}

async function submitAction(mode) {
    if (!pendingAction) return;
    const app = pendingAction.app;
    const body = buildEvaluationBody();
    if (body === null) return;

    if (mode === "reject" && !isCurrentRoundPending(app)) {
        const remark = (body.adminRemark || "").trim();
        const response = await fetch("/admin/applications/" + app.applicationId + "/status", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ status: "REJECTED", remark: remark })
        });
        if (UI.redirectIfUnauthorized(response)) return;
        closeActionModal();
        if (response.ok) {
            window.UI.showToast("Application rejected.", "success");
            refreshMgmtApplicants();
            loadApplications();
        } else {
            window.UI.showToast("Unable to reject this application.", "error");
        }
        return;
    }

    const updated = await postEvaluation(
        "/admin/applications/" + app.applicationId + "/rounds/" + (Number(app.currentRound) || 1) + "/" + mode,
        body,
        [mode === "qualify" ? confirmQualifyBtn : confirmRejectBtn,
         mode === "qualify" ? confirmRejectBtn : confirmQualifyBtn],
        mode === "qualify" ? "Qualifying..." : "Rejecting...");
    if (!updated) return;
    replaceApplication(updated);
    renderMgmtPanel(updated);
    closeActionModal();
    window.UI.showToast(actionSuccessMessage(mode, updated), "success");
}

function storeApplication(updated) {
    const index = applications.findIndex(function(app) {
        return app.applicationId === updated.applicationId;
    });
    if (index >= 0) applications[index] = updated;
    renderUsers();
}

function replaceApplication(updated) {
    storeApplication(updated);
    render();
}

function actionSuccessMessage(mode, updated) {
    if (mode === "reject") return "Application rejected.";
    if (String(updated.status).toUpperCase() === "SELECTED") {
        return "Final round cleared — candidate SELECTED.";
    }
    const jobId = jobIdOf(updated);
    const next = nextConfigured(jobId, updated.currentRound);
    if (next != null) {
        return "Round qualified. Next: " + roundLabel(jobId, updated.currentRound) + ".";
    }
    return "Round qualified. Candidate moved to Round " + updated.currentRound + ".";
}

async function loadMgmtJobs() {
    const response = await fetch("/admin/jobs", { credentials: "include" });
    if (UI.redirectIfUnauthorized(response) || !response.ok) return;
    mgmtJobs = await response.json();
}

function refreshMgmtApplicants() {
    const esc = window.UI.escapeHtml;
    const jobId = mgmtJobSelect.value;
    if (!jobId) {
        mgmtAppSelect.innerHTML = '<option value="">Select Applicant…</option>';
        mgmtAppSelect.disabled = true;
        renderMgmtPanel(null);
        renderRoundConfig();
        return;
    }
    const previousId = mgmtAppSelect.value;
    const applicants = applications.filter(function(app) {
        return jobIdOf(app) === jobId;
    });
    mgmtAppSelect.innerHTML = '<option value="">Select Applicant…</option>' + applicants.map(function(app) {
        const label = (app.studentName || "Student")
            + (app.enrollmentno ? " · " + app.enrollmentno : "")
            + " — " + roundLabel(jobId, app.currentRound || 1);
        return '<option value="' + esc(app.applicationId) + '">' + esc(label) + "</option>";
    }).join("");
    mgmtAppSelect.disabled = false;
    ensureConfig(jobId).then(function() {
        renderRoundConfig();
        const stillListed = applicants.some(function(app) { return app.applicationId === previousId; });
        if (previousId && stillListed) {
            mgmtAppSelect.value = previousId;
            renderMgmtPanel(applications.find(function(app) { return app.applicationId === previousId; }));
        } else {
            renderMgmtPanel(null);
        }
    });
    renderRoundConfig();
}

function renderMgmtPanel(app) {
    if (!mgmtJobSelect.value || !app) {
        mgmtPanel.innerHTML = "";
        return;
    }
    const esc = window.UI.escapeHtml;
    const eligible = !isTerminal(app);
    const jobId = jobIdOf(app);
    const nextRound = eligible ? nextConfigured(jobId, app.currentRound) : null;

    let actionHtml;
    if (!eligible) {
        actionHtml = '<span class="field-hint">No further action — this application is '
            + esc(String(app.status)) + '.</span>';
    } else if (nextRound != null) {
        actionHtml = '<button class="btn btn-success" id="sendNextBtn">Send to Next Round (→ '
            + esc(roundLabel(jobId, nextRound)) + ')</button>';
    } else {
        actionHtml = '<button class="btn btn-success" id="sendNextBtn">Complete Final Round &amp; Select Candidate</button>';
    }

    mgmtPanel.innerHTML =
        '<div class="detail-list">'
        + '<p><strong>Candidate</strong>' + esc(app.studentName || "-")
        + (app.enrollmentno ? " · " + esc(app.enrollmentno) : "") + "</p>"
        + '<p><strong>Status</strong><span class="badge ' + window.UI.badge(app.status) + '">'
        + esc(app.status || "Applied") + "</span></p>"
        + '<p><strong>Progress</strong>Round ' + esc(String(app.currentRound || 1)) + " of "
        + esc(String(app.totalRounds || app.currentRound || 1))
        + " (" + esc(roundLabel(jobId, app.currentRound || 1)) + ")</p>"
        + "</div>"
        + '<div class="mgmt-history">' + window.UI.progressList(app) + "</div>"
        + '<div class="mgmt-actions">' + actionHtml + "</div>";

    const sendButton = document.getElementById("sendNextBtn");
    if (sendButton) {
        sendButton.addEventListener("click", function() { sendToNextRound(sendButton); });
    }
}

async function sendToNextRound(button) {
    const app = applications.find(function(item) { return item.applicationId === mgmtAppSelect.value; });
    if (!app || isTerminal(app)) return;

    const updated = await postEvaluation(
        "/admin/applications/" + app.applicationId + "/advance", {}, [button], "Sending...");
    if (!updated) return;
    replaceApplication(updated);
    renderMgmtPanel(updated);
    refreshMgmtApplicants();
    window.UI.showToast(actionSuccessMessage("qualify", updated), "success");
}

function renderRoundConfig() {
    const jobId = mgmtJobSelect.value;
    if (!jobId) {
        roundConfigSection.style.display = "none";
        return;
    }
    const job = jobsById[jobId];
    roundConfigSection.style.display = "";
    roundConfigTitle.textContent = (job && job.company ? job.company + " — " : "") + "Round Configuration";
    const rounds = jobRoundsCache[jobId] || [];
    const esc = window.UI.escapeHtml;
    if (!rounds.length) {
        roundConfigBody.innerHTML = '<tr><td colspan="4" class="field-hint">'
            + "No rounds configured yet — evaluation falls back to the job's legacy rounds or just Round 1."
            + "</td></tr>";
        return;
    }
    roundConfigBody.innerHTML = rounds.map(function(round) {
        const number = Number(round.roundNumber);
        return "<tr>"
            + "<td><strong>" + number + "</strong></td>"
            + "<td>" + esc(round.roundName || "Round " + number) + "</td>"
            + "<td>" + esc(round.description || "-") + "</td>"
            + '<td><div class="table-actions">'
            + '<button type="button" class="btn btn-small btn-secondary round-edit-btn" data-num="' + number + '">Edit</button>'
            + '<button type="button" class="btn btn-small btn-danger round-delete-btn" data-num="' + number + '">Delete</button>'
            + "</div></td></tr>";
    }).join("");
}

let roundModalEditNumber = null;

function suggestedNextRound(jobId) {
    const numbers = configuredNumbers(jobId).filter(function(number) { return number > 0; });
    return numbers.length ? Math.max.apply(null, numbers) + 1 : 1;
}

function openRoundModal(existingNumber) {
    const jobId = mgmtJobSelect.value;
    if (!jobId) {
        window.UI.showToast("Select a job first.", "warning");
        return;
    }
    roundModalEditNumber = existingNumber || null;
    if (existingNumber) {
        const round = (jobRoundsCache[jobId] || []).find(function(item) {
            return Number(item.roundNumber) === Number(existingNumber);
        }) || {};
        roundModalTitle.textContent = "Edit Round " + existingNumber;
        roundNumberInput.value = existingNumber;
        roundNameInput.value = round.roundName || "";
        roundDescInput.value = round.description || "";
        saveRoundBtn.textContent = "Save Changes";
    } else {
        roundModalTitle.textContent = "Add New Round";
        roundNumberInput.value = suggestedNextRound(jobId);
        roundNameInput.value = "";
        roundDescInput.value = "";
        saveRoundBtn.textContent = "Add Round";
    }
    window.UI.openModal(roundModal);
}

function closeRoundModal() {
    roundModalEditNumber = null;
    window.UI.closeModal(roundModal);
}

roundForm.addEventListener("submit", async function(event) {
    event.preventDefault();
    const jobId = mgmtJobSelect.value;
    if (!jobId) return;
    const number = Number(roundNumberInput.value);
    if (!Number.isInteger(number) || number < 1) {
        window.UI.showToast("Round number must be a positive integer.", "error");
        return;
    }
    const payload = {
        roundNumber: number,
        roundName: roundNameInput.value.trim(),
        description: roundDescInput.value.trim()
    };
    if (roundModalEditNumber == null) {
        const existing = (jobRoundsCache[jobId] || []).some(function(item) {
            return Number(item.roundNumber) === number;
        });
        if (existing) {
            window.UI.showToast("Round " + number + " already exists for this job.", "error");
            return;
        }
    }
    saveRoundBtn.disabled = true;
    try {
        const url = "/admin/jobs/" + jobId + "/rounds"
            + (roundModalEditNumber != null ? "/" + roundModalEditNumber : "");
        const response = await fetch(url, {
            method: roundModalEditNumber != null ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify(payload)
        });
        if (UI.redirectIfUnauthorized(response)) return;
        if (response.ok) {
            closeRoundModal();
            await reloadConfig(jobId);
            renderRoundConfig();
            render();
            rebuildRoundFilter();
            refreshMgmtApplicants();
            window.UI.showToast(roundModalEditNumber != null
                ? "Round " + number + " updated." : "Round " + number + " added.", "success");
        } else {
            const error = await response.json().catch(function() { return {}; });
            window.UI.showToast(error.message || "Unable to save the round.", "error");
        }
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    } finally {
        saveRoundBtn.disabled = false;
    }
});

async function deleteRound(roundNumber) {
    const jobId = mgmtJobSelect.value;
    if (!jobId) return;
    const label = roundLabel(jobId, roundNumber);
    const confirmed = await window.UI.confirmAction(
        "Remove " + label + " from this job's configuration? Applications keep their recorded history.",
        { title: "Delete Round", danger: true, confirmText: "Delete Round" });
    if (!confirmed) return;
    try {
        const response = await fetch("/admin/jobs/" + jobId + "/rounds/" + roundNumber, {
            method: "DELETE",
            credentials: "include"
        });
        if (UI.redirectIfUnauthorized(response)) return;
        if (response.ok) {
            await reloadConfig(jobId);
            renderRoundConfig();
            render();
            rebuildRoundFilter();
            refreshMgmtApplicants();
            window.UI.showToast(label + " removed from the configuration.", "success");
        } else {
            const error = await response.json().catch(function() { return {}; });
            window.UI.showToast(error.message || "Unable to delete the round.", "error");
        }
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    }
}

function openLegacyModal(app) {
    editingApp = app;
    appDetail.innerHTML = `
        <p><strong>Student</strong>${window.UI.escapeHtml(app.studentName || "-")} (${window.UI.escapeHtml(app.enrollmentno || "")})</p>
        <p><strong>Email</strong>${window.UI.escapeHtml(app.studentEmail || "-")}</p>
        <p><strong>Company / Role</strong>${window.UI.escapeHtml(app.company || "-")} - ${window.UI.escapeHtml(app.role || "-")}</p>
        <p><strong>Application ID</strong>${window.UI.escapeHtml(app.applicationId || "-")}</p>
        <p><strong>Current Round</strong>${window.UI.escapeHtml(currentRoundLabel(app))}</p>
        <p><strong>Current Status</strong>${window.UI.escapeHtml(app.status || "Applied")}</p>
        <p><strong>Admin Remark</strong>${window.UI.escapeHtml(app.adminRemark || "-")}</p>
        <div style="grid-column: 1 / -1;">${window.UI.progressList(app)}</div>
    `;
    const desired = app.status || "IN_PROGRESS";
    if (Array.from(statusSelect.options).some(function(o) { return o.value === desired; })) {
        statusSelect.value = desired;
    } else {
        statusSelect.value = "IN_PROGRESS";
    }
    remarkInput.value = app.adminRemark || "";
    statusModal.classList.add("open");
}

function closeLegacyModal() {
    statusModal.classList.remove("open");
}

async function saveStatus() {
    saveStatusBtn.disabled = true;
    const response = await fetch("/admin/applications/" + editingApp.applicationId + "/status", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
            status: statusSelect.value,
            remark: remarkInput.value.trim()
        })
    });
    if (UI.redirectIfUnauthorized(response)) return;
    saveStatusBtn.disabled = false;
    if (response.ok) {
        closeLegacyModal();
        window.UI.showToast("Application status updated.", "success");
        loadApplications();
    } else {
        window.UI.showToast("Unable to update status.", "error");
    }
}

appsBody.addEventListener("click", function(event) {
    const menuButton = event.target.closest(".menu-btn");
    if (menuButton) {
        const menu = document.querySelector('.row-menu[data-menu-for="' + menuButton.dataset.menu + '"]');
        const wasOpen = menu && !menu.classList.contains("hidden");
        closeOpenMenus();
        if (menu && !wasOpen) menu.classList.remove("hidden");
        return;
    }
    const item = event.target.closest(".menu-item");
    if (!item || item.disabled) return;
    const app = applications.find(function(candidate) {
        return candidate.applicationId === item.dataset.id;
    });
    if (!app) return;
    closeOpenMenus();
    if (item.classList.contains("view-btn")) openLegacyModal(app);
    else if (item.classList.contains("qualify-btn")) openActionModal("qualify", app);
    else if (item.classList.contains("reject-btn")) openActionModal("reject", app);
    else if (item.classList.contains("advance-btn")) {
        postEvaluation("/admin/applications/" + app.applicationId + "/advance", {},
                [item], "Sending...").then(function(updated) {
            if (!updated) return;
            replaceApplication(updated);
            renderMgmtPanel(updated);
            refreshMgmtApplicants();
            window.UI.showToast(actionSuccessMessage("qualify", updated), "success");
        });
    } else if (item.classList.contains("status-btn")) openLegacyModal(app);
});

document.addEventListener("click", function(event) {
    if (!event.target.closest(".menu-wrap")) closeOpenMenus();
});

appsBody.addEventListener("change", function(event) {
    const checkbox = event.target.closest(".row-select");
    if (!checkbox) return;
    if (checkbox.checked) selectedIds.add(checkbox.dataset.id);
    else selectedIds.delete(checkbox.dataset.id);
    syncSelectionUi();
});

selectAllCheckbox.addEventListener("change", function() {
    if (selectAllCheckbox.checked) {
        actionableIds.forEach(function(id) { selectedIds.add(id); });
    } else {
        selectedIds.clear();
    }
    document.querySelectorAll(".row-select").forEach(function(box) {
        box.checked = selectedIds.has(box.dataset.id);
    });
    syncSelectionUi();
});

selectQualifiedBtn.addEventListener("click", function() {
    selectedIds.clear();
    actionableIds.forEach(function(id) { selectedIds.add(id); });
    document.querySelectorAll(".row-select").forEach(function(box) {
        box.checked = selectedIds.has(box.dataset.id);
    });
    syncSelectionUi();
});

const exportBtn = document.getElementById("exportBtn");

function currentFilterParams() {
    const params = new URLSearchParams();
    if (jobFilter.value) params.set("jobId", jobFilter.value);
    if (companyFilter.value) params.set("company", companyFilter.value);
    if (roundFilter.value) params.set("round", roundFilter.value);
    if (statusFilter.value) params.set("status", statusFilter.value);
    if (batchFilter.value) params.set("batch", batchFilter.value);
    if (branchFilter.value) params.set("branch", branchFilter.value);
    if (searchBox.value.trim()) params.set("search", searchBox.value.trim());
    if (dateFromFilter.value) params.set("from", dateFromFilter.value);
    if (dateToFilter.value) params.set("to", dateToFilter.value);
    return params;
}

async function downloadApplicationsCsv(button, fields) {
    const params = currentFilterParams();
    if (fields && fields.length) params.set("fields", fields.join(","));
    const query = params.toString();

    window.UI.setButtonLoading(button, true, "Exporting...");
    try {
        const response = await fetch("/admin/applications/export" + (query ? "?" + query : ""),
            { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) {
            const error = await response.json().catch(function() { return {}; });
            window.UI.showToast(error.message || "Could not export the CSV.", "error");
            return;
        }
        const blob = await response.blob();
        const disposition = response.headers.get("Content-Disposition") || "";
        const match = disposition.match(/filename="?([^"]+)"?/);
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = match ? match[1] : "applications.csv";
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(function() { URL.revokeObjectURL(link.href); }, 1000);
        window.UI.showToast("CSV downloaded.", "success");
    } catch (networkError) {
        window.UI.showToast("Network error while exporting. Please retry.", "error");
    } finally {
        window.UI.setButtonLoading(button, false);
    }
}

exportBtn.addEventListener("click", function() {
    downloadApplicationsCsv(exportBtn, null);
});

/* ---------- customize export ---------- */

const customizeExportBtn = document.getElementById("customizeExportBtn");
const exportModal = document.getElementById("exportModal");
const exportFieldsContainer = document.getElementById("exportFieldsContainer");
const exportCountBadge = document.getElementById("exportCountBadge");
const exportStatusHint = document.getElementById("exportStatusHint");
const confirmExportBtn = document.getElementById("confirmExportBtn");
const cancelExportBtn = document.getElementById("cancelExportBtn");
const exportSelectAllBtn = document.getElementById("exportSelectAllBtn");
const exportClearAllBtn = document.getElementById("exportClearAllBtn");
const exportResetBtn = document.getElementById("exportResetBtn");

let exportCatalog = null;

function selectedExportFields() {
    return Array.prototype.slice
        .call(exportFieldsContainer.querySelectorAll('input[type="checkbox"]:checked'))
        .map(function(box) { return box.value; });
}

function updateExportCount() {
    exportCountBadge.textContent = selectedExportFields().length + " selected";
}

function renderExportGroups() {
    const defaults = new Set(exportCatalog.defaults || []);
    const esc = window.UI.escapeHtml;
    const groups = exportCatalog.groups || {};
    const names = Object.keys(groups);
    exportFieldsContainer.innerHTML = names.map(function(group) {
        const boxes = (groups[group] || []).map(function(field) {
            const id = "exp-" + field.replace(/[^a-z0-9]+/gi, "-").toLowerCase();
            return '<label class="checkbox-row" for="' + id + '">'
                + '<input type="checkbox" id="' + id + '" value="' + esc(field) + '"'
                + (defaults.has(field) ? " checked" : "") + ">"
                + '<span>' + esc(field) + (defaults.has(field) ? ' <em class="field-hint">(default)</em>' : "")
                + "</span></label>";
        }).join("");
        return '<div class="export-group"><h4>' + esc(group) + "</h4>" + boxes + "</div>";
    }).join("");
    exportFieldsContainer.addEventListener("change", updateExportCount);
    updateExportCount();
}

async function openExportModal() {
    exportStatusHint.textContent = "";
    if (!exportCatalog) {
        exportFieldsContainer.innerHTML = '<p style="color:var(--text-muted);">Loading available fields...</p>';
        window.UI.openModal(exportModal);
        try {
            const response = await fetch("/admin/applications/export-fields", { credentials: "include" });
            if (UI.redirectIfUnauthorized(response)) return;
            if (!response.ok) throw new Error("Request failed");
            exportCatalog = await response.json();
            renderExportGroups();
        } catch (loadError) {
            exportFieldsContainer.innerHTML = "";
            exportStatusHint.textContent = "Could not load the field list. Close and try again.";
        }
        return;
    }
    renderExportGroups();
    window.UI.openModal(exportModal);
}

if (customizeExportBtn) {
    customizeExportBtn.addEventListener("click", openExportModal);
}
if (cancelExportBtn) {
    cancelExportBtn.addEventListener("click", function() { window.UI.closeModal(exportModal); });
}
if (exportSelectAllBtn) {
    exportSelectAllBtn.addEventListener("click", function() {
        exportFieldsContainer.querySelectorAll('input[type="checkbox"]')
            .forEach(function(box) { box.checked = true; });
        updateExportCount();
    });
}
if (exportClearAllBtn) {
    exportClearAllBtn.addEventListener("click", function() {
        exportFieldsContainer.querySelectorAll('input[type="checkbox"]')
            .forEach(function(box) { box.checked = false; });
        updateExportCount();
    });
}
if (exportResetBtn) {
    exportResetBtn.addEventListener("click", function() {
        const defaults = new Set((exportCatalog && exportCatalog.defaults) || []);
        exportFieldsContainer.querySelectorAll('input[type="checkbox"]')
            .forEach(function(box) { box.checked = defaults.has(box.value); });
        updateExportCount();
    });
}
if (confirmExportBtn) {
    confirmExportBtn.addEventListener("click", function() {
        const fields = selectedExportFields();
        if (!fields.length) {
            exportStatusHint.textContent = "Select at least one column to export.";
            return;
        }
        window.UI.closeModal(exportModal);
        downloadApplicationsCsv(confirmExportBtn, fields);
    });
}

bulkAdvanceBtn.addEventListener("click", function() { runBulkAction("advance"); });
bulkRejectBtn.addEventListener("click", function() { runBulkAction("reject"); });
bulkClearBtn.addEventListener("click", clearSelection);

bulkSetRoundBtn.addEventListener("click", function() {
    setRoundInput.value = 1;
    setRoundRemark.value = "";
    window.UI.openModal(setRoundModal);
});
cancelSetRoundBtn.addEventListener("click", function() { window.UI.closeModal(setRoundModal); });

applySetRoundBtn.addEventListener("click", async function() {
    const ids = Array.from(selectedIds);
    if (ids.length === 0) return;
    const targetRound = Number(setRoundInput.value);
    if (!Number.isInteger(targetRound) || targetRound < 1) {
        window.UI.showToast("Round number must be a positive integer.", "error");
        return;
    }
    window.UI.setButtonLoading(applySetRoundBtn, true, "Applying...");
    try {
        const response = await fetch("/admin/applications/bulk/set-round", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({
                applicationIds: ids,
                targetRound: targetRound,
                adminRemark: setRoundRemark.value.trim() || null
            })
        });
        if (UI.redirectIfUnauthorized(response)) return;
        let summary;
        if (response.ok) {
            summary = await response.json();
        } else {
            const error = await response.json().catch(function() { return {}; });
            window.UI.showToast(error.message || "Could not update the candidates.", "error");
            return;
        }
        (summary.results || []).forEach(function(result) {
            if (result.application) storeApplication(result.application);
        });
        render();
        clearSelection();
        window.UI.closeModal(setRoundModal);
        const moved = Number(summary.moved) || 0;
        const failed = Number(summary.failed) || 0;
        let message = moved + " candidate" + (moved === 1 ? "" : "s")
            + " set to Round " + targetRound + ".";
        if (failed > 0) message += " " + failed + " skipped (finished or already updated).";
        window.UI.showToast(message, failed === 0 ? "success" : (moved > 0 ? "success" : "warning"));
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    } finally {
        window.UI.setButtonLoading(applySetRoundBtn, false);
    }
});

confirmQualifyBtn.addEventListener("click", function() { submitAction("qualify"); });
confirmRejectBtn.addEventListener("click", function() { submitAction("reject"); });
cancelActionBtn.addEventListener("click", closeActionModal);

cancelStatusBtn.addEventListener("click", closeLegacyModal);
saveStatusBtn.addEventListener("click", saveStatus);

addRoundBtn.addEventListener("click", function() { openRoundModal(null); });
cancelRoundBtn.addEventListener("click", closeRoundModal);

roundConfigBody.addEventListener("click", function(event) {
    const editButton = event.target.closest(".round-edit-btn");
    const deleteButton = event.target.closest(".round-delete-btn");
    if (editButton) openRoundModal(Number(editButton.dataset.num));
    else if (deleteButton) deleteRound(Number(deleteButton.dataset.num));
});

searchBox.addEventListener("input", function() { resetAppPage(); render(); });
companyFilter.addEventListener("change", function() { resetAppPage(); render(); });
statusFilter.addEventListener("change", function() { resetAppPage(); render(); });
batchFilter.addEventListener("change", function() { resetAppPage(); render(); });
branchFilter.addEventListener("change", function() { resetAppPage(); render(); });
dateFromFilter.addEventListener("change", function() { resetAppPage(); render(); });
dateToFilter.addEventListener("change", function() { resetAppPage(); render(); });
clearAppFiltersBtn.addEventListener("click", clearAppFilters);

appPagination.addEventListener("click", function(event) {
    const button = event.target.closest(".page-btn");
    if (!button || button.disabled || !button.dataset.apage) return;
    goAppPage(Number(button.dataset.apage));
});

userSearch.addEventListener("input", function() { updateUserSearchClear(); resetUserPage(); ensureUsersLazy().then(renderUsers); });
userSearchClearBtn.addEventListener("click", function() { clearUserSearch(); ensureUsersLazy().then(renderUsers); });
userBatchFilter.addEventListener("change", function() { resetUserPage(); ensureUsersLazy().then(renderUsers); });
userBranchFilter.addEventListener("change", function() { resetUserPage(); ensureUsersLazy().then(renderUsers); });
userStatusFilter.addEventListener("change", function() { resetUserPage(); ensureUsersLazy().then(renderUsers); });
userDateFrom.addEventListener("change", function() { resetUserPage(); renderUsers(); });
userDateTo.addEventListener("change", function() { resetUserPage(); renderUsers(); });
userJobFilter.addEventListener("change", function() { resetUserPage(); ensureUsersLazy().then(renderUsers); });
userClearBtn.addEventListener("click", clearUserFilters);

userPagination.addEventListener("click", function(event) {
    const button = event.target.closest(".page-btn");
    if (!button || button.disabled || !button.dataset.upage) return;
    goUserPage(Number(button.dataset.upage));
});

function findApplicationById(id) {
    return applications.find(function(app) { return app.applicationId === id; }) || null;
}

async function focusRoundManagement(app) {
    const jobId = jobIdOf(app);
    const jobKnown = mgmtJobs.some(function(job) { return job.id === jobId; });
    if (!jobKnown) {
        window.UI.showToast("This job is not listed in Round Management. Use View Details instead.", "warning");
        return;
    }
    mgmtJobSelect.value = jobId;
    refreshMgmtApplicants();
    await ensureConfig(jobId);
    mgmtAppSelect.value = app.applicationId;
    renderMgmtPanel(app);
    const section = document.getElementById("roundMgmtSection");
    if (section && section.scrollIntoView) section.scrollIntoView({ behavior: "smooth", block: "start" });
    window.UI.showToast("Opened " + window.UI.escapeHtml(app.company || "job")
        + " in Round Management.", "info");
}

userResults.addEventListener("click", function(event) {
    const expand = event.target.closest(".user-expand");
    if (expand) {
        const row = expand.closest(".user-row");
        const detail = row && row.querySelector(".user-row-detail");
        if (!detail) return;
        const nowHidden = detail.classList.toggle("hidden");
        expand.classList.toggle("open", !nowHidden);
        expand.setAttribute("aria-expanded", String(!nowHidden));
        const text = expand.querySelector(".user-expand-text");
        if (text) {
            const count = (row && row.dataset.appcount) || "";
            text.textContent = nowHidden
                ? (count ? "View " + count + " applications" : "View applications")
                : "Hide applications";
        }
        return;
    }
    const viewBtn = event.target.closest(".act-view");
    if (viewBtn) {
        const app = findApplicationById(viewBtn.dataset.id);
        if (app) openLegacyModal(app);
        return;
    }
    const manageBtn = event.target.closest(".act-manage");
    if (manageBtn) {
        const app = findApplicationById(manageBtn.dataset.id);
        if (app) focusRoundManagement(app);
    }
});

jobFilter.addEventListener("change", function() {
    rebuildRoundFilter();
});
roundFilter.addEventListener("change", function() { resetAppPage(); render(); });

mgmtJobSelect.addEventListener("change", refreshMgmtApplicants);
mgmtAppSelect.addEventListener("change", function() {
    const app = applications.find(function(item) {
        return item.applicationId === mgmtAppSelect.value;
    });
    renderMgmtPanel(app || null);
});

document.addEventListener("DOMContentLoaded", function() {
    Promise.all([loadApplications(), loadMgmtJobs()]).then(function() {
        fillJobFilters();

        refreshMgmtApplicants();
        rebuildRoundFilter();
        render();
    });
});