const searchBox = document.getElementById("searchBox");
const companyFilter = document.getElementById("companyFilter");
const locationFilter = document.getElementById("locationFilter");
const statusFilter = document.getElementById("statusFilter");
const cardsGrid = document.getElementById("cardsGrid");
const emptyMsg = document.getElementById("emptyMsg");
const showMoreWrap = document.getElementById("showMoreWrap");
const showMoreBtn = document.getElementById("showMoreBtn");
const showMoreCount = document.getElementById("showMoreCount");
const jobDetailsModal = document.getElementById("jobDetailsModal");
const jobDetailsTitle = document.getElementById("jobDetailsTitle");
const jobDetailsBody = document.getElementById("jobDetailsBody");
const jobDetailsClose = document.getElementById("jobDetailsClose");
const jobDetailsCancel = document.getElementById("jobDetailsCancel");

const INITIAL_CARD_COUNT = 6;
const MORE_CARD_INCREMENT = 6;
const SKILL_CHIP_LIMIT = 4;
const ROLE_CHIP_LIMIT = 2;

const ICON = {
    pin: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z"/><circle cx="12" cy="10" r="2.6"/></svg>',
    briefcase: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>',
    users: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.9"/><path d="M16 3.1a4 4 0 0 1 0 7.8"/></svg>',
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
    calendar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/></svg>',
    rupee: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M7 4h10M7 9h10M15.5 4c0 3.5-2 5-4.5 5H7l8 11"/></svg>',
    wallet: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M20 12V8H6a2 2 0 0 1 0-4h12v4"/><path d="M4 6v12a2 2 0 0 0 2 2h14v-4"/><path d="M18 12a2 2 0 0 0 0 4h4v-4Z"/></svg>',
    cap: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M22 9L12 4 2 9l10 5 10-5z"/><path d="M6 11v5c0 1.4 2.7 2.5 6 2.5s6-1.1 6-2.5v-5"/></svg>',
    paperclip: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21.4 11.1 12.3 20a5.5 5.5 0 0 1-7.8-7.8l9.1-9.1a3.7 3.7 0 1 1 5.2 5.2l-9.1 9.1a1.8 1.8 0 1 1-2.6-2.6l8.4-8.4"/></svg>'
};

function truncate(value, limit) {
    var text = String(value || "");
    return text.length > limit ? text.slice(0, limit - 1).trimEnd() + "…" : text;
}

function companyInitials(company) {
    var words = String(company || "").trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) return "?";
    if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
    return (words[0][0] + words[1][0]).toUpperCase();
}

function salaryOf(job) {
    if (job.salaryLpa != null && String(job.salaryLpa).trim() !== "") return String(job.salaryLpa) + " LPA";
    if (job.ctc && String(job.ctc).trim() !== "") return String(job.ctc);
    if (job.stipend && String(job.stipend).trim() !== "") return String(job.stipend);
    return "Not disclosed";
}

function skillsChips(skills) {
    if (skills.length === 0) return '<span class="op-chip op-chip-empty">Not specified</span>';
    const esc = window.UI.escapeHtml;
    const shown = skills.slice(0, SKILL_CHIP_LIMIT).map(function(skill) {
        return '<span class="op-chip">' + esc(skill) + "</span>";
    });
    if (skills.length > SKILL_CHIP_LIMIT) {
        shown.push('<span class="op-chip op-chip-more" title="' + esc(skills.slice(SKILL_CHIP_LIMIT).join(", ")) + '">+' + (skills.length - SKILL_CHIP_LIMIT) + "</span>");
    }
    return shown.join("");
}

function eligibilityBlock(job) {
    const esc = window.UI.escapeHtml;
    const chips = [];
    if (job.minCgpa != null) chips.push("CGPA ≥ " + job.minCgpa);
    if (job.minTenth != null) chips.push("10th ≥ " + job.minTenth + "%");
    if (job.minTwelfth != null) chips.push("12th ≥ " + job.minTwelfth + "%");
    if (job.minGraduation != null) chips.push("Graduation ≥ " + job.minGraduation + "%");
    if (job.eligibility && String(job.eligibility).trim()) {
        chips.push(String(job.eligibility).trim());
    }
    if (chips.length === 0) {
        return '<div class="op-block"><span class="op-label">Eligibility</span>'
            + '<div class="op-courses">No specific criteria</div></div>';
    }
    return '<div class="op-block"><span class="op-label">Eligibility</span><div class="op-chips">'
        + chips.map(function(chip) {
            return '<span class="op-chip op-chip-elig" title="' + esc(chip) + '">' + esc(truncate(chip, 34)) + "</span>";
        }).join("")
        + "</div></div>";
}

let allJobs = [];
let appliedJobIds = [];
let pendingApplication = null;
let visibleCount = INITIAL_CARD_COUNT;
let lastFiltered = [];

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
    companyFilter.innerHTML = '<option value="">All companies</option>' + options(allJobs.map(j => j.company));
    locationFilter.innerHTML = '<option value="">All locations</option>' + options(allJobs.map(j => j.location));

    const statuses = [...new Set(allJobs.map(j => j.status || "Open").filter(Boolean))].sort();
    statusFilter.innerHTML = '<option value="">All statuses</option>' +
        statuses.map(function(s) { return '<option value="' + esc(s) + '">' + esc(s) + "</option>"; }).join("");
}

function updateShowMore(total) {
    if (total <= INITIAL_CARD_COUNT) {
        showMoreWrap.style.display = "none";
        return;
    }
    const hidden = Math.max(0, total - visibleCount);
    showMoreWrap.style.display = "flex";
    if (hidden > 0) {
        showMoreBtn.textContent = "...more (" + hidden + " hidden)";
        showMoreBtn.setAttribute("aria-expanded", "false");
    } else {
        showMoreBtn.textContent = "...less";
        showMoreBtn.setAttribute("aria-expanded", "true");
    }
    showMoreCount.textContent = "Showing " + Math.min(visibleCount, total) + " of " + total +
        (total === 1 ? " placement" : " placements");
}

function render() {
    const search = searchBox.value.toLowerCase().trim();
    const company = companyFilter.value;
    const location = locationFilter.value;
    const status = statusFilter.value;

    const filtered = allJobs.filter(function(job) {
        const roles = splitList(job.jobRole || job.jobType);
        const text = (job.company + " " + roles.join(" ") + " " + (job.skills || "") + " " +
            (job.batch || "") + " " + (job.eligibility || "")).toLowerCase();
        const matchesSearch = !search || text.includes(search);
        const matchesCompany = !company || job.company === company;
        const matchesLocation = !location || job.location === location;
        const jobStatus = job.status || "Open";
        const matchesStatus = !status || jobStatus === status;
        return matchesSearch && matchesCompany && matchesLocation && matchesStatus;
    });

    lastFiltered = filtered;
    visibleCount = Math.min(visibleCount, filtered.length);
    if (visibleCount < INITIAL_CARD_COUNT) visibleCount = INITIAL_CARD_COUNT;

    emptyMsg.style.display = filtered.length === 0 ? "block" : "none";
    updateShowMore(filtered.length);

    cardsGrid.innerHTML = filtered.slice(0, visibleCount).map(function(job) {
        const esc = window.UI.escapeHtml;
        const applied = appliedJobIds.includes(job.id);
        const roles = splitList(job.jobRole || job.jobType);
        const skills = splitList(job.skills);
        const courses = splitList(job.courses);
        const desc = (job.jobDescription || "").trim();
        const initials = companyInitials(job.company);
        const salary = salaryOf(job);
        const deadline = window.UI.fmtDate(job.applicationDeadline);
        const status = job.status || "Open";
        const closed = String(status).toLowerCase() === "closed";

        const roleText = roles.length === 0 ? "Role not specified"
            : (roles.length <= ROLE_CHIP_LIMIT ? roles.join(", ")
                : roles.slice(0, ROLE_CHIP_LIMIT).join(", ") + " +" + (roles.length - ROLE_CHIP_LIMIT) + " more");
        const roleTitle = roles.join(", ");

        const metaItems = [
            { icon: ICON.pin, text: job.location || "Location not specified" },
            { icon: ICON.briefcase, text: job.mode || "Work mode not specified" },
            { icon: ICON.users, text: job.openings ? job.openings + (/\d/.test(job.openings) ? " openings" : "") : "Openings not specified" },
            { icon: ICON.clock, text: job.duration || "Duration not specified" }
        ];

        return `
            <article class="op-card${closed ? " is-closed" : ""}">
                <header class="op-card-head">
                    <div class="op-logo" aria-hidden="true">${esc(initials)}</div>
                    <div class="op-head-text">
                        <h3 class="op-company" title="${esc(job.company || "Company")}">${esc(job.company || "Company")}</h3>
                        <div class="op-role" title="${esc(roleTitle)}">${esc(roleText)}</div>
                    </div>
                    <div class="op-head-tags">
                        <span class="op-tag op-tag-status" data-status="${esc(status)}">${esc(status)}</span>
                        <span class="op-tag" title="Eligible batches">${esc(job.batch || "All batches")}</span>
                    </div>
                </header>

                <div class="op-pay">
                    <span class="op-pay-value">${esc(salary)}</span>
                    <span class="op-pay-label">${job.stipend && !job.salaryLpa ? "stipend" : "CTC / LPA"}</span>
                </div>

                <ul class="op-meta">
                    ${metaItems.map(function(item) {
                        return `<li>${item.icon}${esc(item.text)}</li>`;
                    }).join("")}
                </ul>

                ${eligibilityBlock(job)}

                <div class="op-block">
                    <span class="op-label">Key skills</span>
                    <div class="op-chips">${skillsChips(skills)}</div>
                </div>

                <div class="op-block">
                    <span class="op-label">Eligible courses</span>
                    <div class="op-courses" title="${esc(courses.join(", "))}">${esc(courses.length ? courses.join(", ") : "All courses")}</div>
                </div>

                ${desc ? '<p class="op-desc" title="' + esc(truncate(desc, 220)) + '">' + esc(desc) + "</p>" : ""}

                <footer class="op-actions">
                    <button class="btn btn-small${applied ? " applied" : ""}" data-job-id="${esc(job.id)}" ${applied || closed ? "disabled" : ""}>
                        ${closed ? "Closed" : (applied ? "Applied" : "Apply Now")}
                    </button>
                    <button class="btn btn-small btn-secondary" data-details="${esc(job.id)}">View Details</button>
                    ${(job.documents || []).length ? `
                        <button class="op-docs-btn" data-docs='${esc(JSON.stringify(job.documents))}' data-company="${esc(job.company)}" type="button" title="View attached documents" aria-label="View attached documents">
                            ${ICON.paperclip}
                        </button>` : ""}
                </footer>
            </article>
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
    const job = allJobs.find(function(entry) { return entry.id === jobId; }) || {};
    document.getElementById("resumeModalTitle").textContent = "Add your resume for " +
        (job.company || "this company");
    document.getElementById("resumeUrlInput").value = "";
    const courseGroup = document.getElementById("courseSelectionGroup");
    const courseInput = document.getElementById("selectedCourseInput");
    const courses = splitList(job.courses);
    courseGroup.hidden = courses.length === 0;
    courseInput.required = courses.length > 0;
    courseInput.innerHTML = '<option value="">Select a course</option>' + courses.map(function(course) {
        return '<option value="' + UI.escapeHtml(course) + '">' + UI.escapeHtml(course) + "</option>";
    }).join("");
    const roleGroup = document.getElementById("roleSelectionGroup");
    const roleInput = document.getElementById("selectedRoleInput");
    const roles = splitList(job.jobRole || job.jobType);
    roleGroup.hidden = roles.length === 0;
    roleInput.required = roles.length > 0;
    roleInput.innerHTML = '<option value="">Select a job role</option>' + roles.map(function(role) {
        return '<option value="' + UI.escapeHtml(role) + '">' + UI.escapeHtml(role) + "</option>";
    }).join("");
    UI.openModal(document.getElementById("resumeModal"));
}

function splitList(value) {
    return String(value || "").split(",").map(function(entry) {
        return entry.trim();
    }).filter(Boolean);
}

async function submitApplication() {
    if (!pendingApplication) return;
    const resumeInput = document.getElementById("resumeUrlInput");
    if (!resumeInput.reportValidity()) return;
    // The submit button is type="button", so the browser never runs the
    // "required" checks on the role/course selects. Validate them here.
    const roleInput = document.getElementById("selectedRoleInput");
    const courseInput = document.getElementById("selectedCourseInput");
    if (roleInput.required && !roleInput.reportValidity()) return;
    if (courseInput.required && !courseInput.reportValidity()) return;

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
            body: JSON.stringify({
                jobId: jobId,
                resumeUrl: resumeInput.value.trim(),
                selectedCourse: document.getElementById("selectedCourseInput").value,
                selectedRole: document.getElementById("selectedRoleInput").value
            })
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

function detailRow(icon, label, value) {
    if (value === null || value === undefined || value === "") return "";
    const esc = window.UI.escapeHtml;
    return '<div class="jd-row"><dt>' + icon + esc(label) + "</dt><dd>" + esc(value) + "</dd></div>";
}

function openJobDetails(jobId) {
    const job = allJobs.find(function(entry) { return entry.id === jobId; });
    if (!job) return;
    const esc = window.UI.escapeHtml;
    const roles = splitList(job.jobRole || job.jobType);
    const skills = splitList(job.skills);
    const courses = splitList(job.courses);
    const desc = (job.jobDescription || "").trim();
    const criteria = [];
    if (job.minCgpa != null) criteria.push("CGPA ≥ " + job.minCgpa);
    if (job.minTenth != null) criteria.push("10th ≥ " + job.minTenth + "%");
    if (job.minTwelfth != null) criteria.push("12th ≥ " + job.minTwelfth + "%");
    if (job.minGraduation != null) criteria.push("Graduation ≥ " + job.minGraduation + "%");

    jobDetailsTitle.textContent = (job.company || "Company") + " — Opportunity details";

    jobDetailsBody.innerHTML = `
        <div class="jd-head">
            <div class="op-logo op-logo-lg" aria-hidden="true">${esc(companyInitials(job.company))}</div>
            <div>
                <h3 class="jd-company">${esc(job.company || "Company")}</h3>
                <p class="jd-roles">${esc(roles.length ? roles.join(" · ") : "Role not specified")}</p>
            </div>
        </div>
        <dl class="jd-grid">
            ${detailRow(ICON.rupee, "Compensation", salaryOf(job))}
            ${detailRow(ICON.wallet, "Stipend", job.stipend)}
            ${detailRow(ICON.pin, "Location", job.location)}
            ${detailRow(ICON.briefcase, "Work mode", job.mode)}
            ${detailRow(ICON.users, "Openings", job.openings)}
            ${detailRow(ICON.clock, "Duration", job.duration)}
            ${detailRow(ICON.calendar, "Deadline", window.UI.fmtDate(job.applicationDeadline))}
            ${detailRow(ICON.cap, "Eligible batches", job.batch)}
            ${detailRow(ICON.cap, "Eligible branches", job.branch)}
            ${detailRow(ICON.calendar, "Likely joining", job.likelyDateOfJoining)}
            ${detailRow(ICON.rupee, "Program fee", job.programFee)}
        </dl>
        ${criteria.length || (job.eligibility && job.eligibility.trim()) ? `
            <div class="jd-section">
                <span class="op-label">Eligibility</span>
                <div class="op-chips">${(criteria.concat(job.eligibility ? [String(job.eligibility).trim()] : []))
                    .map(function(c) { return '<span class="op-chip op-chip-elig">' + esc(c) + "</span>"; }).join("")}</div>
            </div>` : ""}
        ${skills.length ? `<div class="jd-section"><span class="op-label">Skills</span>
            <div class="op-chips">${skills.map(function(s) { return '<span class="op-chip">' + esc(s) + "</span>"; }).join("")}</div></div>` : ""}
        ${courses.length ? `<div class="jd-section"><span class="op-label">Eligible courses</span>
            <div class="op-courses">${esc(courses.join(", "))}</div></div>` : ""}
        ${desc ? `<div class="jd-section"><span class="op-label">About this role</span>
            <p class="jd-desc">${esc(desc)}</p></div>` : ""}
        ${job.website || job.applicationLink ? `<div class="jd-section"><span class="op-label">Links</span>
            <div class="op-chips">${job.website ? `<a class="op-chip" href="${esc(job.website)}" target="_blank" rel="noopener">Website</a>` : ""}
            ${job.applicationLink ? `<a class="op-chip" href="${esc(job.applicationLink)}" target="_blank" rel="noopener">Apply link</a>` : ""}</div></div>` : ""}
    `;
    window.UI.openModal(jobDetailsModal);
}

if (jobDetailsClose) jobDetailsClose.addEventListener("click", function() { window.UI.closeModal(jobDetailsModal); });
if (jobDetailsCancel) jobDetailsCancel.addEventListener("click", function() { window.UI.closeModal(jobDetailsModal); });
if (jobDetailsModal) jobDetailsModal.addEventListener("click", function(event) {
    if (event.target === this) window.UI.closeModal(this);
});

cardsGrid.addEventListener("click", function(event) {
    const details = event.target.closest("[data-details]");
    if (details) {
        openJobDetails(details.dataset.details);
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

const refilter = function() {
    visibleCount = INITIAL_CARD_COUNT;
    render();
};

searchBox.addEventListener("input", refilter);
companyFilter.addEventListener("change", refilter);
locationFilter.addEventListener("change", refilter);
statusFilter.addEventListener("change", refilter);

showMoreBtn.addEventListener("click", function() {
    if (visibleCount >= lastFiltered.length) {
        visibleCount = INITIAL_CARD_COUNT;
    } else {
        visibleCount = Math.min(visibleCount + MORE_CARD_INCREMENT, lastFiltered.length);
    }
    render();
    if (visibleCount <= INITIAL_CARD_COUNT) {
        cardsGrid.scrollIntoView({ behavior: "smooth", block: "start" });
    }
});

document.addEventListener("DOMContentLoaded", function() {
    // The placements feed and this student's own applications are independent,
    // so fetch them concurrently instead of serially waiting on one first.
    Promise.all([loadAppliedJobs(), loadJobs()]).then(function() {
        // Re-render once both resolve so the "Applied" badges reflect applications
        // that finished loading in parallel with the placements feed.
        render();
    });
});