const searchBox = document.getElementById("searchBox");
const companyFilter = document.getElementById("companyFilter");
const jobsBody = document.getElementById("jobsBody");
const emptyMsg = document.getElementById("emptyMsg");
const addJobBtn = document.getElementById("addJobBtn");
const jobModal = document.getElementById("jobModal");
const modalTitle = document.getElementById("modalTitle");
const saveJobBtn = document.getElementById("saveJobBtn");
const cancelJobBtn = document.getElementById("cancelJobBtn");

const fields = {
    company: document.getElementById("fCompany"),
    jobRole: document.getElementById("fJobRole"),
    courses: document.getElementById("fCourses"),
    jobType: document.getElementById("fRole"),
    location: document.getElementById("fLocation"),
    eligibility: document.getElementById("fEligibility"),
    skills: document.getElementById("fSkills"),
    applicationDeadline: document.getElementById("fDeadline"),
    openings: document.getElementById("fOpenings"),
    status: document.getElementById("fStatus"),
    jobDescription: document.getElementById("fDescription"),
    website: document.getElementById("fWebsite"),
    driveType: document.getElementById("fDriveType"),
    mode: document.getElementById("fMode"),
    likelyDateOfJoining: document.getElementById("fDoj"),
    salaryLpa: document.getElementById("fSalaryLpa"),
    batchRangesList: document.getElementById("batchRangesList"),
    branch: document.getElementById("fBranch"),
    minTenth: document.getElementById("fMinTenth"),
    minTwelfth: document.getElementById("fMinTwelfth"),
    minGraduation: document.getElementById("fMinGrad"),
    minCgpa: document.getElementById("fMinCgpa")
};

const roundsEditor = document.getElementById("roundsEditor");
const addRoundBtn = document.getElementById("addRoundBtn");
const addBatchRangeBtn = document.getElementById("addBatchRangeBtn");

const roundsModal = document.getElementById("roundsModal");
const roundsJobInfo = document.getElementById("roundsJobInfo");
const roundsList = document.getElementById("roundsList");
const roundFormLabel = document.getElementById("roundFormLabel");
const newRoundNumber = document.getElementById("newRoundNumber");
const newRoundName = document.getElementById("newRoundName");
const newRoundDescription = document.getElementById("newRoundDescription");
const addRoundSubmitBtn = document.getElementById("addRoundSubmitBtn");
const closeRoundsBtn = document.getElementById("closeRoundsBtn");

let roundsJobId = null;
let editingDialogRound = null;
let currentModalJobId = null;

let configCache = {};
const configPromises = {};

function ensureConfig(jobId) {
    if (!jobId) return Promise.resolve([]);
    if (configPromises[jobId]) return configPromises[jobId];
    configPromises[jobId] = fetch("/admin/jobs/" + jobId + "/rounds", { credentials: "include" })
        .then(function(response) {
            if (UI.redirectIfUnauthorized(response)) return [];
            return response.ok ? response.json() : [];
        })
        .then(function(list) { configCache[jobId] = Array.isArray(list) ? list : []; return configCache[jobId]; })
        .catch(function() { configCache[jobId] = []; return []; });
    return configPromises[jobId];
}

function reloadConfig(jobId) {
    delete configPromises[jobId];
    return ensureConfig(jobId);
}

let jobs = [];
let editingId = null;

async function loadJobs() {
    try {
        const response = await fetch("/admin/jobs", { credentials: "include" });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) throw new Error("Could not load placements");
        const payload = await response.json();
        if (!Array.isArray(payload)) throw new Error("Unexpected placements payload");
        jobs = payload;
        const esc = window.UI.escapeHtml;
        const companies = [...new Set(jobs.map(j => j.company).filter(Boolean))].sort();
        companyFilter.innerHTML = '<option value="">All companies</option>' +
            companies.map(c => '<option value="' + esc(c) + '">' + esc(c) + "</option>").join("");
        render();
    } catch (error) {
        window.UI.showToast("Could not load placements. Please retry.", "error");
    }
}

function render() {
    const search = searchBox.value.toLowerCase().trim();
    const company = companyFilter.value;
    const filtered = jobs.filter(function(job) {
        const text = (job.company + " " + job.jobRole + " " + job.jobType).toLowerCase();
        return (!search || text.includes(search)) && (!company || job.company === company);
    });
    emptyMsg.style.display = filtered.length === 0 ? "block" : "none";
    const esc = window.UI.escapeHtml;
    jobsBody.innerHTML = filtered.map(function(job) {
        return `
            <tr>
                <td>${esc(job.company || "-")}</td>
                <td>${esc(job.jobRole || job.jobType || "-")}</td>
                <td>${esc(job.salaryLpa || job.stipend || "-")}</td>
                <td>${esc(job.location || "-")}</td>
                <td>${esc(job.eligibility || "-")}</td>
                <td>${esc(window.UI.fmtDate(job.applicationDeadline))}</td>
                <td><span class="badge ${job.status === "Closed" ? "Closed" : "Open"}">${esc(job.status || "Open")}</span></td>
                <td>
                    <button class="btn btn-small btn-secondary docs-btn" data-id="${esc(job.id)}">Documents</button>
                    <button class="btn btn-small btn-secondary rounds-btn" data-id="${esc(job.id)}">Rounds</button>
                    <button class="btn btn-small btn-secondary edit-btn" data-id="${esc(job.id)}">Edit</button>
                    <button class="btn btn-small btn-danger delete-btn" data-id="${esc(job.id)}">Delete</button>
                </td>
            </tr>
        `;
    }).join("");
}

function toDateInputValue(value) {
    if (!value) return "";
    var text = String(value).trim();
    var match = text.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
    if (!match) return "";
    return match[1] + "-" + match[2].padStart(2, "0") + "-" + match[3].padStart(2, "0");
}

function addBatchRangeRow(range) {
    const row = document.createElement("div");
    row.className = "batch-range-row";
    row.style.cssText = "display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr) auto;gap:8px;align-items:center;margin-bottom:8px;";

    const fromYear = document.createElement("input");
    fromYear.type = "number";
    fromYear.min = "1000";
    fromYear.max = "9999";
    fromYear.step = "1";
    fromYear.className = "batch-from-year";
    fromYear.placeholder = "From Year";
    fromYear.setAttribute("aria-label", "Eligible batch from year");
    fromYear.value = range ? range.from : "";

    const toYear = document.createElement("input");
    toYear.type = "number";
    toYear.min = "1000";
    toYear.max = "9999";
    toYear.step = "1";
    toYear.className = "batch-to-year";
    toYear.placeholder = "To Year";
    toYear.setAttribute("aria-label", "Eligible batch to year");
    toYear.value = range ? range.to : "";

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "btn btn-small btn-secondary batch-range-remove";
    removeButton.title = "Remove batch range";
    removeButton.setAttribute("aria-label", "Remove batch range");
    removeButton.textContent = "×";
    row.append(fromYear, toYear, removeButton);
    fields.batchRangesList.appendChild(row);
}

function setBatchRanges(value) {
    fields.batchRangesList.innerHTML = "";
    const ranges = String(value || "").split(",").map(function(token) {
        const years = token.match(/\d{4}/g) || [];
        if (!years.length) return null;
        return { from: years[0], to: years.length > 1 ? years[1] : years[0] };
    }).filter(Boolean);
    (ranges.length ? ranges : [null]).forEach(addBatchRangeRow);
}

function collectBatchRanges() {
    return Array.from(fields.batchRangesList.querySelectorAll(".batch-range-row")).map(function(row) {
        return {
            from: row.querySelector(".batch-from-year").value.trim(),
            to: row.querySelector(".batch-to-year").value.trim()
        };
    }).filter(function(range) {
        return range.from || range.to;
    });
}

function openModal(job) {
    editingId = job ? job.id : null;
    currentModalJobId = job ? job.id : null;
    modalTitle.textContent = job ? "Edit Placement" : "Add Placement";
    fields.company.value = job ? job.company || "" : "";
    fields.jobRole.value = job ? job.jobRole || "" : "";
    fields.courses.value = job ? job.courses || "" : "";
    fields.jobType.value = job ? job.jobType || "" : "";
    fields.location.value = job ? job.location || "" : "";
    fields.eligibility.value = job ? job.eligibility || "" : "";
    fields.skills.value = job ? job.skills || "" : "";
    fields.applicationDeadline.value = toDateInputValue(job ? job.applicationDeadline : "");
    fields.openings.value = job ? job.openings || "" : "";
    fields.status.value = job ? (job.status || "Open") : "Open";
    fields.jobDescription.value = job ? job.jobDescription || "" : "";
    fields.website.value = job ? job.website || "" : "";
    fields.driveType.value = job ? job.driveType || "" : "";
    fields.mode.value = job ? job.mode || "" : "";
    fields.likelyDateOfJoining.value = toDateInputValue(job ? job.likelyDateOfJoining : "");
    fields.salaryLpa.value = job ? (job.salaryLpa != null ? job.salaryLpa : "") : "";
    setBatchRanges(job ? job.batch : "");
    fields.branch.value = job ? job.branch || "" : "";
    fields.minTenth.value = job && job.minTenth != null ? job.minTenth : "";
    fields.minTwelfth.value = job && job.minTwelfth != null ? job.minTwelfth : "";
    fields.minGraduation.value = job && job.minGraduation != null ? job.minGraduation : "";
    fields.minCgpa.value = job && job.minCgpa != null ? job.minCgpa : "";

    renderRoundsEditor(job && Array.isArray(job.rounds) ? job.rounds.map(function(entry) {
        return { round: entry.round, name: entry.name, description: null };
    }) : []);
    if (job) {
        ensureConfig(job.id).then(function(list) {
            if (list.length && currentModalJobId === job.id) {
                renderRoundsEditor(list.map(function(round) {
                    return { round: round.roundNumber, name: round.roundName, description: round.description };
                }));
            }
        });
    }
    jobModal.classList.add("open");
}

function addRoundRow(round) {
    const row = document.createElement("div");
    row.className = "round-row";
    const numberInput = document.createElement("input");
    numberInput.type = "number";
    numberInput.className = "round-number-input";
    numberInput.min = "1";
    numberInput.placeholder = "#";
    const nameInput = document.createElement("input");
    nameInput.type = "text";
    nameInput.className = "round-name-input";
    nameInput.placeholder = "Round name (e.g. Technical Interview)";
    const descInput = document.createElement("input");
    descInput.type = "text";
    descInput.className = "round-desc-input";
    descInput.placeholder = "Description (optional)";
    if (round && typeof round === "object") {
        numberInput.value = round.round;
        nameInput.value = round.name || "";
        descInput.value = round.description || "";
    } else {
        numberInput.value = round != null ? round : nextRoundNumber();
    }
    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.className = "round-remove-btn";
    removeBtn.title = "Remove round";
    removeBtn.textContent = "✕";
    row.append(numberInput, nameInput, descInput, removeBtn);
    roundsEditor.appendChild(row);
}

function nextRoundNumber() {
    return collectRounds().reduce(function (max, entry) {
        return Math.max(max, entry.round);
    }, 0) + 1;
}

function renderRoundsEditor(rounds) {
    roundsEditor.innerHTML = "";
    (rounds || []).forEach(function(entry) { addRoundRow(entry); });
}

function collectRounds() {
    return Array.from(roundsEditor.querySelectorAll(".round-row")).map(function(row) {
        return {
            round: Number(row.querySelector(".round-number-input").value),
            name: row.querySelector(".round-name-input").value.trim(),
            description: row.querySelector(".round-desc-input").value.trim()
        };
    }).filter(function(entry) {
        return Number.isInteger(entry.round) && entry.round > 0;
    }).sort(function(a, b) { return a.round - b.round; });
}

function closeModal() {
    jobModal.classList.remove("open");
}

function collectJob() {
    function num(field) {
        return field.value !== "" ? Number(field.value) : undefined;
    }
    return {
        company: fields.company.value.trim(),
        jobRole: fields.jobRole.value.trim(),
        jobType: fields.jobType.value.trim(),
        location: fields.location.value.trim(),
        eligibility: fields.eligibility.value.trim(),
        skills: fields.skills.value.trim(),
        applicationDeadline: fields.applicationDeadline.value.trim(),
        openings: fields.openings.value.trim(),
        status: fields.status.value || "Open",
        jobDescription: fields.jobDescription.value.trim(),
        website: fields.website.value.trim(),
        driveType: fields.driveType.value.trim(),
        mode: fields.mode.value.trim(),
        likelyDateOfJoining: fields.likelyDateOfJoining.value.trim(),
        salaryLpa: fields.salaryLpa.value.trim(),
        batch: collectBatchRanges().map(function(range) {
            return range.from + "-" + range.to;
        }).join(", "),
        courses: fields.courses.value.split(",").map(function(course) {
            return course.trim();
        }).filter(Boolean).join(", "),
        branch: fields.branch.value.trim(),
        minTenth: num(fields.minTenth),
        minTwelfth: num(fields.minTwelfth),
        minGraduation: num(fields.minGraduation),
        minCgpa: num(fields.minCgpa)
    };
}

async function createRound(jobId, entry) {
    const response = await fetch("/admin/jobs/" + jobId + "/rounds", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ roundNumber: entry.round, roundName: entry.name, description: entry.description })
    });
    if (UI.redirectIfUnauthorized(response)) return false;
    if (!response.ok) {
        const error = await response.json().catch(function() { return {}; });
        window.UI.showToast(error.message || ("Could not save round " + entry.round + "."), "error");
    }
    return response.ok;
}

async function updateRound(jobId, number, entry) {
    const response = await fetch("/admin/jobs/" + jobId + "/rounds/" + number, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ roundNumber: number, roundName: entry.name, description: entry.description })
    });
    if (UI.redirectIfUnauthorized(response)) return false;
    if (!response.ok) {
        const error = await response.json().catch(function() { return {}; });
        window.UI.showToast(error.message || ("Could not update round " + number + "."), "error");
    }
    return response.ok;
}

async function removeRound(jobId, number) {
    const response = await fetch("/admin/jobs/" + jobId + "/rounds/" + number, {
        method: "DELETE",
        credentials: "include"
    });
    if (UI.redirectIfUnauthorized(response)) return false;
    if (!response.ok) {
        const error = await response.json().catch(function() { return {}; });
        window.UI.showToast(error.message || ("Could not delete round " + number + "."), "error");
    }
    return response.ok;
}

async function syncJobRounds(jobId, desired) {
    const response = await fetch("/admin/jobs/" + jobId + "/rounds", { credentials: "include" });
    if (UI.redirectIfUnauthorized(response)) return false;
    const existing = response.ok ? await response.json() : [];
    const existingByNumber = new Map(existing.map(function(round) {
        return [Number(round.roundNumber), round];
    }));
    const desiredNumbers = new Set(desired.map(function(entry) { return entry.round; }));

    for (const entry of desired) {
        const current = existingByNumber.get(entry.round);
        if (!current) {
            if (!(await createRound(jobId, entry))) return false;
        } else if ((current.roundName || "") !== (entry.name || "")
                || (current.description || "") !== (entry.description || "")) {
            if (!(await updateRound(jobId, entry.round, entry))) return false;
        }
    }
    for (const round of existing) {
        if (!desiredNumbers.has(Number(round.roundNumber))) {
            if (!(await removeRound(jobId, Number(round.roundNumber)))) return false;
        }
    }
    return true;
}

async function saveJob() {
    if (!fields.company.value.trim()) {
        window.UI.showToast("Company name is required.", "warning");
        return;
    }
    for (const range of collectBatchRanges()) {
        if (!/^\d{4}$/.test(range.from) || !/^\d{4}$/.test(range.to)) {
            window.UI.showToast("Enter both eligible batch years in YYYY format.", "warning");
            return;
        }
        if (Number(range.from) > Number(range.to)) {
            window.UI.showToast("From Year cannot be greater than To Year.", "warning");
            return;
        }
    }
    const rounds = collectRounds();
    const seen = new Set();
    for (const entry of rounds) {
        if (seen.has(entry.round)) {
            window.UI.showToast("Round numbers must be unique.", "warning");
            return;
        }
        seen.add(entry.round);
    }
    saveJobBtn.disabled = true;
    try {
        const data = collectJob();
        const url = editingId ? "/admin/jobs/" + editingId : "/admin/jobs";
        const method = editingId ? "PUT" : "POST";
        const response = await fetch(url, {
            method: method,
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify(data)
        });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) {
            window.UI.showToast("Unable to save placement.", "error");
            return;
        }
        const saved = await response.json();

        await syncJobRounds(saved.id || editingId, rounds);
        // The rounds are memoized per job; drop the cache so the modal shows the
        // rounds that were just saved instead of the pre-save list.
        reloadConfig(saved.id || editingId);
        closeModal();
        window.UI.showToast(editingId ? "Placement updated." : "Placement added.", "success");
        loadJobs();
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    } finally {
        saveJobBtn.disabled = false;
    }
}

async function deleteJob(id) {
    const confirmed = await window.UI.confirmAction(
        "This will delete the placement and all associated applications. This cannot be undone.",
        { title: "Delete Placement", danger: true, confirmText: "Delete" });
    if (!confirmed) return;
    const response = await fetch("/admin/jobs/" + id, {
        method: "DELETE",
        credentials: "include"
    });
    if (UI.redirectIfUnauthorized(response)) return;
    if (response.ok) {
        window.UI.showToast("Placement deleted.", "success");
        loadJobs();
    } else {
        const error = await response.json().catch(function() { return {}; });
        window.UI.showToast(error.message || "Could not delete the placement.", "error");
    }
}

addJobBtn.addEventListener("click", function() {
    openModal(null);
});

addRoundBtn.addEventListener("click", function() {
    addRoundRow(null);
});

addBatchRangeBtn.addEventListener("click", function() {
    addBatchRangeRow(null);
});

fields.batchRangesList.addEventListener("click", function(event) {
    const removeButton = event.target.closest(".batch-range-remove");
    if (!removeButton) return;
    removeButton.closest(".batch-range-row").remove();
    if (!fields.batchRangesList.querySelector(".batch-range-row")) addBatchRangeRow(null);
});

roundsEditor.addEventListener("click", function(event) {
    const removeButton = event.target.closest(".round-remove-btn");
    if (removeButton) removeButton.closest(".round-row").remove();
});

cancelJobBtn.addEventListener("click", closeModal);

saveJobBtn.addEventListener("click", saveJob);

function resetDialogRoundForm() {
    editingDialogRound = null;
    roundFormLabel.textContent = "Add Round";
    addRoundSubmitBtn.textContent = "Add Round";
    newRoundNumber.readOnly = false;
}

function openRoundsModal(job) {
    roundsJobId = job ? job.id : null;
    const esc = window.UI.escapeHtml;
    roundsJobInfo.innerHTML = '<p><strong>Job</strong>' + esc(job.company || "-") + " — "
        + esc(job.jobRole || job.jobType || "-") + ' <span class="mgmt-job-id">ID: ' + esc(job.id || "") + "</span></p>";
    resetDialogRoundForm();
    newRoundNumber.value = "";
    newRoundName.value = "";
    newRoundDescription.value = "";
    window.UI.openModal(roundsModal);
    loadConfiguredRounds();
}

async function loadConfiguredRounds() {
    if (!roundsJobId) return;
    const requestedJobId = roundsJobId;
    const rounds = await reloadConfig(requestedJobId);
    // The user may have opened Rounds for a different job while this was in
    // flight; rendering now would overwrite the newer modal with stale data.
    if (roundsJobId !== requestedJobId) return;
    const esc = window.UI.escapeHtml;
    if (!editingDialogRound) {

        const max = rounds.reduce(function(value, round) {
            return Math.max(value, Number(round.roundNumber) || 0);
        }, 0);
        newRoundNumber.value = max + 1;
    }
    if (!rounds.length) {
        roundsList.innerHTML = '<p class="field-hint">No rounds configured yet — applications on this job '
            + "follow a single-round process until you add one here.</p>";
        return;
    }
    roundsList.innerHTML = rounds.map(function(round) {
        return '<div class="round-config-item">'
            + '<span class="badge Pending">✓ Round ' + esc(round.roundNumber) + "</span>"
            + '<div class="round-config-info"><div class="progress-head">' + esc(round.roundName || "Round "
                + round.roundNumber) + "</div>"
            + (round.description ? '<div class="progress-meta">' + esc(round.description) + "</div>" : "")
            + "</div>"
            + '<div class="table-actions">'
            + '<button type="button" class="btn btn-small btn-secondary dialog-edit-btn" data-num="'
                + esc(round.roundNumber) + '">Edit</button>'
            + '<button type="button" class="btn btn-small btn-danger dialog-delete-btn" data-num="'
                + esc(round.roundNumber) + '">Delete</button>'
            + "</div></div>";
    }).join("");
}

async function submitNewRound() {
    if (!roundsJobId) return;
    const number = Number(newRoundNumber.value);
    if (!Number.isInteger(number) || number < 1) {
        window.UI.showToast("Enter a positive round number.", "warning");
        return;
    }
    if (!editingDialogRound) {
        const rounds = configCache[roundsJobId] || [];
        if (rounds.some(function(round) { return Number(round.roundNumber) === number; })) {
            window.UI.showToast("Round " + number + " already exists for this job.", "error");
            return;
        }
    }
    addRoundSubmitBtn.disabled = true;
    try {
        let ok;
        if (editingDialogRound != null) {
            ok = await updateRound(roundsJobId, editingDialogRound, {
                round: editingDialogRound,
                name: newRoundName.value.trim(),
                description: newRoundDescription.value.trim()
            });
        } else {
            ok = await createRound(roundsJobId, {
                round: number,
                name: newRoundName.value.trim(),
                description: newRoundDescription.value.trim()
            });
        }
        if (ok) {
            window.UI.showToast(editingDialogRound != null
                ? "Round " + editingDialogRound + " updated." : "Round " + number + " added.", "success");
            resetDialogRoundForm();
            loadConfiguredRounds();
        }
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    } finally {
        addRoundSubmitBtn.disabled = false;
    }
}

async function deleteDialogRound(number) {
    if (!roundsJobId) return;
    const confirmed = await window.UI.confirmAction(
        "Remove Round " + number + " from this job's configuration? Applications keep their recorded history.",
        { title: "Delete Round", danger: true, confirmText: "Delete Round" });
    if (!confirmed) return;
    if (await removeRound(roundsJobId, number)) {
        if (editingDialogRound === number) resetDialogRoundForm();
        loadConfiguredRounds();
    }
}

addRoundSubmitBtn.addEventListener("click", submitNewRound);
closeRoundsBtn.addEventListener("click", function() { window.UI.closeModal(roundsModal); });

roundsList.addEventListener("click", function(event) {
    const editButton = event.target.closest(".dialog-edit-btn");
    const deleteButton = event.target.closest(".dialog-delete-btn");
    if (!editButton && !deleteButton) return;
    const number = Number(editButton ? editButton.dataset.num : deleteButton.dataset.num);
    if (editButton) {
        const round = (configCache[roundsJobId] || []).find(function(item) {
            return Number(item.roundNumber) === number;
        });
        if (!round) return;
        editingDialogRound = number;
        roundFormLabel.textContent = "Edit Round " + number;
        addRoundSubmitBtn.textContent = "Save Changes";
        newRoundNumber.value = number;
        newRoundNumber.readOnly = true;
        newRoundName.value = round.roundName || "";
        newRoundDescription.value = round.description || "";
    } else {
        deleteDialogRound(number);
    }
});

jobsBody.addEventListener("click", function(event) {
    const editBtn = event.target.closest(".edit-btn");
    const deleteBtn = event.target.closest(".delete-btn");
    const roundsBtn = event.target.closest(".rounds-btn");
    const docsBtn = event.target.closest(".docs-btn");
    if (editBtn) {
        const job = jobs.find(j => j.id === editBtn.dataset.id);
        openModal(job);
    } else if (deleteBtn) {
        deleteJob(deleteBtn.dataset.id);
    } else if (roundsBtn) {
        const job = jobs.find(function(item) { return item.id === roundsBtn.dataset.id; });
        if (job) openRoundsModal(job);
    } else if (docsBtn) {
        const job = jobs.find(function(item) { return item.id === docsBtn.dataset.id; });
        if (job) openDocsModal(job);
    }
});

searchBox.addEventListener("input", render);
companyFilter.addEventListener("change", render);

const exportJobsBtn = document.getElementById("exportJobsBtn");

exportJobsBtn.addEventListener("click", async function() {
    window.UI.setButtonLoading(exportJobsBtn, true, "Exporting...");
    try {
        const response = await fetch("/admin/placements/export", { credentials: "include" });
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
        link.download = match ? match[1] : "placements.csv";
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(function() { URL.revokeObjectURL(link.href); }, 1000);
        window.UI.showToast("CSV downloaded.", "success");
    } catch (networkError) {
        window.UI.showToast("Network error while exporting. Please retry.", "error");
    } finally {
        window.UI.setButtonLoading(exportJobsBtn, false);
    }
});

let docsJobId = null;
let docsReplaceId = null;
const docsModal = document.getElementById("docsModal");
const docsJobInfo = document.getElementById("docsJobInfo");
const docsList = document.getElementById("docsList");
const docsFileInput = document.getElementById("docsFileInput");
const docsReplaceInput = document.getElementById("docsReplaceInput");
const uploadDocsBtn = document.getElementById("uploadDocsBtn");
const closeDocsBtn = document.getElementById("closeDocsBtn");

function currentDocs() {
    const job = jobs.find(function(j) { return j.id === docsJobId; });
    return (job && Array.isArray(job.documents)) ? job.documents : [];
}

function renderDocs() {
    const esc = window.UI.escapeHtml;
    const docList = currentDocs();
    if (!docList.length) {
        docsList.innerHTML = '<p class="field-hint">No documents uploaded yet. Upload a job poster, brochure or JD PDF.</p>';
        return;
    }
    docsList.innerHTML = docList.map(function(doc, index) {
        const size = doc.size ? (Math.round(doc.size / 1024) + " KB") : "";
        return '<div class="round-config-item">'
            + '<div class="round-config-info">'
            + '<a class="doc-link" href="' + esc(doc.fileUrl) + '" target="_blank" rel="noopener">'
            + '<strong>' + esc(doc.fileName || ("Document " + (index + 1))) + "</strong></a>"
            + (size ? '<div class="progress-meta">' + esc(size) + " · " + esc(doc.resourceType || "file") + "</div>" : "")
            + "</div>"
            + '<div class="table-actions">'
            + '<button type="button" class="btn btn-small btn-secondary doc-replace-btn" data-id="'
                + esc(doc.publicId) + '">Replace</button>'
            + '<button type="button" class="btn btn-small btn-danger doc-delete-btn" data-id="'
                + esc(doc.publicId) + '">Delete</button>'
            + "</div></div>";
    }).join("");
}

function openDocsModal(job) {
    docsJobId = job ? job.id : null;
    docsReplaceId = null;
    const esc = window.UI.escapeHtml;
    docsJobInfo.innerHTML = '<p><strong>Job</strong>' + esc(job.company || "-") + " — "
        + esc(job.jobRole || job.jobType || "-") + "</p>";
    window.UI.openModal(docsModal);
    renderDocs();
}

async function uploadDocs(file) {
    if (!file) return confirmUploadDone();
    if (!docsJobId) return;
    const formData = new FormData();
    formData.append("file", file);
    window.UI.setButtonLoading(uploadDocsBtn, true, "Uploading...");
    try {
        const documentUrl = "/admin/jobs/" + docsJobId + "/documents";
        const url = docsReplaceId
            ? documentUrl + "?publicId=" + encodeURIComponent(docsReplaceId)
            : documentUrl;
        const response = await fetch(url, {
            method: docsReplaceId ? "PUT" : "POST",
            credentials: "include",
            body: formData
        });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) {
            const error = await response.json().catch(function() { return {}; });
            window.UI.showToast(error.message || "Could not upload the document.", "error");
            return;
        }
        window.UI.showToast(docsReplaceId ? "Document replaced." : "Document uploaded.", "success");
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    } finally {
        window.UI.setButtonLoading(uploadDocsBtn, false);
    }
}

function confirmUploadDone() {
    docsReplaceId = null;
    if (docsFileInput) docsFileInput.value = "";
    if (docsReplaceInput) docsReplaceInput.value = "";
    loadJobs().then(renderDocs);
}

docsList.addEventListener("click", function(event) {
    const deleteBtn = event.target.closest(".doc-delete-btn");
    const replaceBtn = event.target.closest(".doc-replace-btn");
    if (!docsJobId) return;
    if (deleteBtn) {
        deleteDoc(deleteBtn.dataset.id);
    } else if (replaceBtn) {
        docsReplaceId = replaceBtn.dataset.id;
        docsReplaceInput.click();
    }
});

async function deleteDoc(publicId) {
    const confirmed = await window.UI.confirmAction(
        "Delete this document from the placement? The stored file is also removed.",
        { title: "Delete Document", danger: true, confirmText: "Delete" });
    if (!confirmed) return;
    try {
        const response = await fetch("/admin/jobs/" + docsJobId + "/documents?publicId="
            + encodeURIComponent(publicId), {
            method: "DELETE",
            credentials: "include"
        });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) {
            const error = await response.json().catch(function() { return {}; });
            window.UI.showToast(error.message || "Could not delete the document.", "error");
            return;
        }
        window.UI.showToast("Document deleted.", "success");
        loadJobs().then(renderDocs);
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    }
}

uploadDocsBtn.addEventListener("click", function() {
    docsReplaceId = null;
    docsFileInput.click();
});

docsFileInput.addEventListener("change", function() {
    const file = docsFileInput.files && docsFileInput.files[0];
    uploadDocs(file).then(confirmUploadDone);
});

docsReplaceInput.addEventListener("change", function() {
    const file = docsReplaceInput.files && docsReplaceInput.files[0];
    if (file) {
        uploadDocs(file).then(confirmUploadDone);
    } else {
        confirmUploadDone();
    }
});

closeDocsBtn.addEventListener("click", function() { window.UI.closeModal(docsModal); });

document.addEventListener("DOMContentLoaded", loadJobs);