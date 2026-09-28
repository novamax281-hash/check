const searchBox = document.getElementById("searchBox");
const batchFilter = document.getElementById("batchFilter");
const branchFilter = document.getElementById("branchFilter");
const companyFilter = document.getElementById("companyFilter");
const roleFilter = document.getElementById("roleFilter");
const skillFilter = document.getElementById("skillFilter");
const eligibilityFilter = document.getElementById("eligibilityFilter");
const locationFilter = document.getElementById("locationFilter");
const periodFilter = document.getElementById("periodFilter");
const statusFilter = document.getElementById("statusFilter");
const dateFromFilter = document.getElementById("dateFrom");
const dateToFilter = document.getElementById("dateTo");
const upcomingFilter = document.getElementById("upcomingFilter");
const cardsGrid = document.getElementById("cardsGrid");
const emptyMsg = document.getElementById("emptyMsg");

let allJobs = [];
let appliedJobIds = [];
let pendingApplication = null;

function roleOf(job) {
    return (job.jobRole && String(job.jobRole).trim()) ? String(job.jobRole).trim() : job.jobType;
}

function parseDeadline(value) {
    if (!value) return null;
    var s = String(value).trim();
    var m = s.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/);
    if (m) return new Date(+m[1], +m[2] - 1, +m[3]);
    m = s.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})/);
    if (m) {
        var year = +m[3];

        var month = +m[1] > 12 ? +m[2] : +m[1];
        var day = +m[1] > 12 ? +m[1] : +m[2];
        if (month >= 1 && month <= 12) return new Date(year, month - 1, day);
    }
    m = s.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2})$/);
    if (m) {
        var yy = +m[3];
        yy += yy < 70 ? 2000 : 1900;
        var month2 = +m[1] > 12 ? +m[2] : +m[1];
        var day2 = +m[1] > 12 ? +m[1] : +m[2];
        if (month2 >= 1 && month2 <= 12) return new Date(yy, month2 - 1, day2);
    }
    var d = new Date(s);
    if (!isNaN(d.getTime())) return d;
    return null;
}

function pad2(value) {
    return String(value).padStart(2, "0");
}

function periodOf(value) {
    var date = parseDeadline(value);
    if (!date) return null;
    return {
        key: pad2(date.getMonth() + 1) + "/" + String(date.getFullYear()).slice(-2),
        sort: date.getFullYear() * 100 + (date.getMonth() + 1)
    };
}

function isUpcoming(job) {
    if (job.status === "Closed") return false;
    var date = parseDeadline(job.applicationDeadline);
    if (date) {
        var startOfToday = new Date();
        startOfToday.setHours(0, 0, 0, 0);
        return date.getTime() >= startOfToday.getTime();
    }
    return true;
}

async function loadJobs() {
    try {
        const response = await fetch("/user/placements", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) {
            allJobs = [];
            emptyMsg.style.display = "block";
            window.UI.showToast("Could not load placements. Please try again.", "error");
            return;
        }
        const data = await response.json().catch(function () { return []; });
        allJobs = Array.isArray(data) ? data : [];
        fillFilters();
        render();
    } catch (e) {
        allJobs = [];
        emptyMsg.style.display = "block";
    }
}

async function loadAppliedJobs() {
    try {
        const response = await fetch("/user/applications", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (response.ok) {
            const applications = await response.json();
            appliedJobIds = (applications || []).map(function(app) {
                return app.jobId;
            });
        }
    } catch (e) {
    }
}

function fillFilters() {
    const esc = window.UI.escapeHtml;
    const options = function(list) {
        const unique = [...new Set(list.filter(Boolean))].sort();
        return unique.map(function(value) {
            return '<option value="' + esc(value) + '">' + esc(value) + "</option>";
        }).join("");
    };
    batchFilter.innerHTML = '<option value="">All batches</option>' +
        options(allJobs.map(j => j.batch));
    branchFilter.innerHTML = '<option value="">All branches</option>' +
        options(allJobs.map(j => j.branch));
    companyFilter.innerHTML = '<option value="">All companies</option>' + options(allJobs.map(j => j.company));
    roleFilter.innerHTML = '<option value="">All roles</option>' + options(allJobs.map(j => roleOf(j)));
    skillFilter.innerHTML = '<option value="">All skills</option>' + options(allJobs.map(j => j.skills));
    eligibilityFilter.innerHTML = '<option value="">All eligibility</option>' + options(allJobs.map(j => j.eligibility));
    locationFilter.innerHTML = '<option value="">All locations</option>' + options(allJobs.map(j => j.location));

    const periods = {};
    allJobs.forEach(function(job) {
        const p = periodOf(job.applicationDeadline);
        if (p) periods[p.key] = p.sort;
    });
    const sortedKeys = Object.keys(periods).sort(function(a, b) { return periods[a] - periods[b]; });
    periodFilter.innerHTML = '<option value="">All periods</option>' +
        sortedKeys.map(function(key) { return '<option value="' + esc(key) + '">' + esc(key) + "</option>"; }).join("");

    const statuses = [...new Set(allJobs.map(j => j.status || "Open").filter(Boolean))].sort();
    statusFilter.innerHTML = '<option value="">All statuses</option>' +
        statuses.map(function(s) { return '<option value="' + esc(s) + '">' + esc(s) + "</option>"; }).join("");
}

function render() {
    const search = searchBox.value.toLowerCase().trim();
    const batch = batchFilter.value;
    const branch = branchFilter.value;
    const company = companyFilter.value;
    const role = roleFilter.value;
    const skill = skillFilter.value;
    const eligibility = eligibilityFilter.value;
    const location = locationFilter.value;
    const period = periodFilter.value;
    const status = statusFilter.value;
    const upcomingOnly = upcomingFilter.checked;

    const fromMs = dateFromFilter.value ? new Date(dateFromFilter.value + "T00:00:00").getTime() : null;
    const toMs = dateToFilter.value ? new Date(dateToFilter.value + "T23:59:59").getTime() : null;

    const filtered = allJobs.filter(function(job) {
        const text = (job.company + " " + roleOf(job)).toLowerCase();
        const matchesSearch = !search || text.includes(search);
        const matchesBatch = !batch || window.UI.batchMatches(job.batch, batch);
        const matchesBranch = !branch || job.branch === branch;
        const matchesCompany = !company || job.company === company;
        const matchesRole = !role || roleOf(job) === role;
        const matchesSkill = !skill || (job.skills && job.skills.includes(skill));
        const matchesEligibility = !eligibility || (job.eligibility && job.eligibility.includes(eligibility));
        const matchesLocation = !location || job.location === location;
        const jobStatus = job.status || "Open";
        const matchesStatus = !status || jobStatus === status;
        const p = periodOf(job.applicationDeadline);
        const matchesPeriod = !period || (p && p.key === period);
        const matchesUpcoming = !upcomingOnly || isUpcoming(job);
        const jobDate = parseDeadline(job.applicationDeadline);
        const jobMs = jobDate ? jobDate.getTime() : null;
        const matchesDate = (!fromMs && !toMs) ||
            (jobMs !== null &&
                (!fromMs || jobMs >= fromMs) &&
                (!toMs || jobMs <= toMs));
        return matchesSearch && matchesBatch && matchesBranch && matchesCompany && matchesRole && matchesSkill && matchesEligibility &&
            matchesLocation && matchesStatus && matchesPeriod && matchesUpcoming && matchesDate;
    });

    emptyMsg.style.display = filtered.length === 0 ? "block" : "none";

    cardsGrid.innerHTML = filtered.map(function(job) {
        const applied = appliedJobIds.includes(job.id);
        const esc = window.UI.escapeHtml;
        const hasDocs = (job.documents || []).length > 0;
        const desc = (job.jobDescription || "").trim();
        const descLong = desc.length > 160;
        const salary = (job.salaryLpa != null && String(job.salaryLpa).trim() !== "")
        ? String(job.salaryLpa) + " LPA" : (job.stipend || "N/A");
        const critParts = [];
        if (job.minCgpa != null) critParts.push("CGPA >= " + job.minCgpa);
        if (job.minTenth != null) critParts.push("10th >= " + job.minTenth + "%");
        if (job.minTwelfth != null) critParts.push("12th >= " + job.minTwelfth + "%");
        if (job.minGraduation != null) critParts.push("Graduation >= " + job.minGraduation + "%");
        const criteria = critParts.length ? critParts.join(" · ") : "";
        return `
            <div class="placement-card">
                <h3>${esc(job.company || "Company")}</h3>
                <div class="role">${esc(roleOf(job) || "Role")}</div>
                <div class="meta">
                    <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/></svg> ${esc(salary)}</span>
                    <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z"/><circle cx="12" cy="10" r="2.6"/></svg> ${esc(job.location || "N/A")}</span>
                    <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M22 9L12 4 2 9l10 5 10-5z"/><path d="M6 11v5c0 1.4 2.7 2.5 6 2.5s6-1.1 6-2.5v-5"/></svg> ${esc(job.batch || "All batches")}</span>
                    <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/></svg> Deadline: ${esc(window.UI.fmtDate(job.applicationDeadline))}</span>
                    <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg> Openings: ${esc(job.openings || "N/A")}</span>
                </div>
                <p><strong>Eligibility:</strong> ${esc(job.eligibility || "N/A")}</p>
                ${criteria ? '<p><strong>Criteria:</strong> ' + esc(criteria) + "</p>" : ""}
                <p><strong>Skills:</strong> ${esc(job.skills || "N/A")}</p>
                ${desc ? '<p class="desc">' + esc(desc) + "</p>" : ""}
                ${descLong ? '<button type="button" class="desc-toggle">View more</button>' : ""}
                <div class="card-actions">
                    ${hasDocs ? `
                        <button class="eye-btn" data-docs='${esc(JSON.stringify(job.documents))}' data-company="${esc(job.company)}" type="button" title="View attached documents" aria-label="View attached documents">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/><circle cx="12" cy="12" r="3"/></svg>
                        </button>` : ""}
                    ${applied ? "" : ""}
                    <button class="btn btn-small" data-job-id="${esc(job.id)}" ${applied ? "disabled" : ""}>
                        ${applied ? "Applied" : "Apply Now"}
                    </button>
                </div>
            </div>
        `;
    }).join("");
}

/* ---------- attached document preview ---------- */

let docModalEl = null;

function closeDocModal() {
    if (docModalEl && docModalEl.parentNode) docModalEl.parentNode.removeChild(docModalEl);
    docModalEl = null;
}

function openDocModal(documents, company) {
    closeDocModal();

    const esc = window.UI.escapeHtml;
    const modal = document.createElement("div");
    modal.className = "modal";
    modal.innerHTML = `
        <div class="modal-box modal-lg">
            <div class="modal-header">
                <h2>${esc(company || "Job")} — Documents</h2>
                <button class="modal-close" data-doc-close aria-label="Close">&times;</button>
            </div>
            <div class="doc-list">
                ${documents.map(function(d, i) {
                    const name = d.fileName || ("Document " + (i + 1));
                    const ops = '<a href="' + esc(d.fileUrl || "") + '" target="_blank" rel="noopener" class="btn btn-small">Open</a>';
                    return '<div class="doc-item">' +
                        '<span class="doc-name">' + esc(name) + '</span>' +
                        '<span class="doc-size">' + (d.size ? Math.round(d.size / 1024) + " KB" : "") + '</span>' +
                        ops + "</div>";
                }).join("")}
            </div>
            <div class="modal-actions">
                <button class="btn" data-doc-close>Close</button>
            </div>
        </div>
    `;

    modal.addEventListener("click", function(e) {
        if (e.target === modal || e.target.closest("[data-doc-close]")) closeDocModal();
    });
    document.body.appendChild(modal);
    requestAnimationFrame(function() { modal.classList.add("open"); });
    docModalEl = modal;
    if (docModalEl.querySelector(".modal-close")) docModalEl.querySelector(".modal-close").focus();
}

function applyToJob(jobId, button) {
    pendingApplication = { jobId: jobId, button: button };
    document.getElementById("resumeModalTitle").textContent = "Add your resume for " +
        ((allJobs.find(function(job) { return job.id === jobId; }) || {}).company || "this company");
    document.getElementById("resumeUrlInput").value = "";
    UI.openModal(document.getElementById("resumeModal"));
}

async function submitApplication() {
    if (!pendingApplication) return;
    const resumeInput = document.getElementById("resumeUrlInput");
    if (!resumeInput.reportValidity()) return;

    const jobId = pendingApplication.jobId;
    const button = pendingApplication.button;
    const submitButton = document.getElementById("submitApplicationBtn");
    button.disabled = true;
    button.textContent = "Applying...";
    submitButton.disabled = true;
    try {
        const response = await fetch("/user/apply", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ jobId: jobId, resumeUrl: resumeInput.value.trim() })
        });
        if (UI.redirectIfUnauthorized(response)) return;
        const raw = await response.text();
        let message = raw;
        try {
            const parsed = JSON.parse(raw);
            if (parsed && parsed.message) message = parsed.message;
        } catch (parseError) {}
        if (response.ok) {
            button.textContent = "Applied";
            appliedJobIds.push(jobId);
            pendingApplication = null;
            UI.closeModal(document.getElementById("resumeModal"));
            window.UI.showToast("Application submitted successfully!", "success");
        } else {
            button.textContent = "Apply Now";
            button.disabled = false;
            window.UI.showToast(message || "Unable to apply. Please try again.", "error");
        }
    } catch (error) {
        button.textContent = "Apply Now";
        button.disabled = false;
        window.UI.showToast("Unable to apply. Please try again.", "error");
    } finally {
        submitButton.disabled = false;
    }
}

cardsGrid.addEventListener("click", function(event) {
    const toggle = event.target.closest(".desc-toggle");
    if (toggle) {
        const descEl = toggle.parentElement.querySelector(".desc");
        if (descEl) {
            const expanded = descEl.classList.toggle("expanded");
            toggle.textContent = expanded ? "Show less" : "View more";
        }
        return;
    }
    const eye = event.target.closest("[data-docs]");
    if (eye) {
        let docs = [];
        try { docs = JSON.parse(eye.getAttribute("data-docs")) || []; } catch (e) {}
        openDocModal(docs, eye.getAttribute("data-company"));
        return;
    }
    const button = event.target.closest("[data-job-id]");
    if (!button || button.disabled) return;
    applyToJob(button.dataset.jobId, button);
});

document.getElementById("submitApplicationBtn").addEventListener("click", submitApplication);
document.getElementById("cancelResumeBtn").addEventListener("click", function() {
    pendingApplication = null;
    UI.closeModal(document.getElementById("resumeModal"));
});
document.getElementById("resumeModal").addEventListener("click", function(event) {
    if (event.target === this) {
        pendingApplication = null;
        UI.closeModal(this);
    }
});

searchBox.addEventListener("input", render);
batchFilter.addEventListener("change", render);
branchFilter.addEventListener("change", render);
companyFilter.addEventListener("change", render);
roleFilter.addEventListener("change", render);
skillFilter.addEventListener("change", render);
eligibilityFilter.addEventListener("change", render);
locationFilter.addEventListener("change", render);
periodFilter.addEventListener("change", render);
statusFilter.addEventListener("change", render);
dateFromFilter.addEventListener("change", render);
dateToFilter.addEventListener("change", render);
upcomingFilter.addEventListener("change", render);

document.addEventListener("DOMContentLoaded", async function() {
    await loadAppliedJobs();
    await loadJobs();
});