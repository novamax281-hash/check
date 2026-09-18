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
    jobType: document.getElementById("fRole"),
    stipend: document.getElementById("fSalary"),
    location: document.getElementById("fLocation"),
    eligibility: document.getElementById("fEligibility"),
    skills: document.getElementById("fSkills"),
    applicationDeadline: document.getElementById("fDeadline"),
    openings: document.getElementById("fOpenings"),
    status: document.getElementById("fStatus"),
    jobDescription: document.getElementById("fDescription")
};

const roundsEditor = document.getElementById("roundsEditor");
const addRoundBtn = document.getElementById("addRoundBtn");

const roundsModal = document.getElementById("roundsModal");
const roundsJobInfo = document.getElementById("roundsJobInfo");
const roundsList = document.getElementById("roundsList");
const roundFormLabel = document.getElementById("roundFormLabel");
const newRoundNumber = document.getElementById("newRoundNumber");
const newRoundName = document.getElementById("newRoundName");
const newRoundDescription = document.getElementById("newRoundDescription");
const addRoundSubmitBtn = document.getElementById("addRoundSubmitBtn");
const closeRoundsBtn = document.getElementById("closeRoundsBtn");

const coordinatorsModal = document.getElementById("coordinatorsModal");
const coordinatorsJobInfo = document.getElementById("coordinatorsJobInfo");
const coordinatorsList = document.getElementById("coordinatorsList");
const saveCoordinatorsBtn = document.getElementById("saveCoordinatorsBtn");
const cancelCoordinatorsBtn = document.getElementById("cancelCoordinatorsBtn");

let coordinatorsCache = null;
let coordinatorsJobId = null;

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
    const response = await fetch("/admin/jobs", { credentials: "include" });
    if (UI.redirectIfUnauthorized(response)) return;
    jobs = await response.json();
    const esc = window.UI.escapeHtml;
    const companies = [...new Set(jobs.map(j => j.company).filter(Boolean))].sort();
    companyFilter.innerHTML = '<option value="">All companies</option>' +
        companies.map(c => '<option value="' + esc(c) + '">' + esc(c) + "</option>").join("");
    render();
}

function render() {
    const search = searchBox.value.toLowerCase().trim();
    const company = companyFilter.value;
    const filtered = jobs.filter(function(job) {
        const text = (job.company + " " + job.jobType).toLowerCase();
        return (!search || text.includes(search)) && (!company || job.company === company);
    });
    emptyMsg.style.display = filtered.length === 0 ? "block" : "none";
    const esc = window.UI.escapeHtml;
    jobsBody.innerHTML = filtered.map(function(job) {
        return `
            <tr>
                <td>${esc(job.company || "-")}</td>
                <td>${esc(job.jobType || "-")}</td>
                <td>${esc(job.stipend || "-")}</td>
                <td>${esc(job.location || "-")}</td>
                <td>${esc(job.eligibility || "-")}</td>
                <td>${esc(job.applicationDeadline || "-")}</td>
                <td><span class="badge ${job.status === "Closed" ? "Closed" : "Open"}">${esc(job.status || "Open")}</span></td>
                <td>
                    <button class="btn btn-small btn-secondary coordinators-btn" data-id="${esc(job.id)}">Coordinators</button>
                    <button class="btn btn-small btn-secondary rounds-btn" data-id="${esc(job.id)}">Rounds</button>
                    <button class="btn btn-small btn-secondary edit-btn" data-id="${esc(job.id)}">Edit</button>
                    <button class="btn btn-small btn-danger delete-btn" data-id="${esc(job.id)}">Delete</button>
                </td>
            </tr>
        `;
    }).join("");
}

function openModal(job) {
    editingId = job ? job.id : null;
    currentModalJobId = job ? job.id : null;
    modalTitle.textContent = job ? "Edit Placement" : "Add Placement";
    fields.company.value = job ? job.company || "" : "";
    fields.jobType.value = job ? job.jobType || "" : "";
    fields.stipend.value = job ? job.stipend || "" : "";
    fields.location.value = job ? job.location || "" : "";
    fields.eligibility.value = job ? job.eligibility || "" : "";
    fields.skills.value = job ? job.skills || "" : "";
    fields.applicationDeadline.value = job ? job.applicationDeadline || "" : "";
    fields.openings.value = job ? job.openings || "" : "";
    fields.status.value = job ? (job.status || "Open") : "Open";
    fields.jobDescription.value = job ? job.jobDescription || "" : "";

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
    return {
        company: fields.company.value.trim(),
        jobType: fields.jobType.value.trim(),
        stipend: fields.stipend.value.trim(),
        location: fields.location.value.trim(),
        eligibility: fields.eligibility.value.trim(),
        skills: fields.skills.value.trim(),
        applicationDeadline: fields.applicationDeadline.value.trim(),
        openings: fields.openings.value.trim(),
        status: fields.status.value || "Open",
        jobDescription: fields.jobDescription.value.trim()
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

roundsEditor.addEventListener("click", function(event) {
    const removeButton = event.target.closest(".round-remove-btn");
    if (removeButton) removeButton.closest(".round-row").remove();
});

cancelJobBtn.addEventListener("click", closeModal);

saveJobBtn.addEventListener("click", saveJob);

function ensureCoordinators() {
    if (coordinatorsCache) return Promise.resolve(coordinatorsCache);
    return fetch("/admin/coordinators", { credentials: "include" })
        .then(function (response) {
            if (UI.redirectIfUnauthorized(response)) return [];
            return response.ok ? response.json() : [];
        })
        .then(function (list) {
            coordinatorsCache = Array.isArray(list) ? list : [];
            return coordinatorsCache;
        })
        .catch(function () { coordinatorsCache = []; return coordinatorsCache; });
}

async function openCoordinatorsModal(job) {
    coordinatorsJobId = job ? job.id : null;
    const esc = window.UI.escapeHtml;
    coordinatorsJobInfo.innerHTML = '<p><strong>Job</strong>' + esc(job.company || "-") + " — "
        + esc(job.jobType || "-") + "</p>";
    coordinatorsList.innerHTML = '<p class="field-hint">Loading coordinators...</p>';
    window.UI.openModal(coordinatorsModal);
    const coordinators = await ensureCoordinators();
    if (!coordinators.length) {
        coordinatorsList.innerHTML = '<p class="field-hint">No coordinators yet. Add one from the Coordinators page first.</p>';
        return;
    }
    const assigned = Array.isArray(job.coordinatorIds) ? job.coordinatorIds : [];
    coordinatorsList.innerHTML = coordinators.map(function (coordinator) {
        const label = coordinator.name || coordinator.enrollmentno || coordinator.id;
        const meta = [coordinator.enrollmentno, coordinator.department].filter(Boolean).join(" · ");
        const checked = assigned.indexOf(coordinator.id) !== -1 ? " checked" : "";
        return '<label class="selection-item">' +
            '<input type="checkbox" value="' + esc(coordinator.id) + '"' + checked + ">" +
            '<span class="selection-name">' + esc(label) +
            (meta ? '<span class="selection-meta">' + esc(meta) + "</span>" : "") +
            "</span></label>";
    }).join("");
}

async function saveCoordinators() {
    if (!coordinatorsJobId) return;
    const ids = Array.prototype.slice.call(coordinatorsList.querySelectorAll('input[type="checkbox"]:checked'))
        .map(function (box) { return box.value; });
    saveCoordinatorsBtn.disabled = true;
    try {
        const response = await fetch("/admin/jobs/" + coordinatorsJobId + "/coordinators", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ coordinatorIds: ids })
        });
        if (UI.redirectIfUnauthorized(response)) return;
        if (!response.ok) {
            const error = await response.json().catch(function () { return {}; });
            window.UI.showToast(error.message || "Could not save coordinators.", "error");
            return;
        }
        window.UI.showToast("Coordinators updated.", "success");
        window.UI.closeModal(coordinatorsModal);
        loadJobs();
    } catch (networkError) {
        window.UI.showToast("Network error. Please retry.", "error");
    } finally {
        saveCoordinatorsBtn.disabled = false;
    }
}

cancelCoordinatorsBtn.addEventListener("click", function () { window.UI.closeModal(coordinatorsModal); });

saveCoordinatorsBtn.addEventListener("click", saveCoordinators);

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
        + esc(job.jobType || "-") + ' <span class="mgmt-job-id">ID: ' + esc(job.id || "") + "</span></p>";
    resetDialogRoundForm();
    newRoundNumber.value = "";
    newRoundName.value = "";
    newRoundDescription.value = "";
    window.UI.openModal(roundsModal);
    loadConfiguredRounds();
}

async function loadConfiguredRounds() {
    if (!roundsJobId) return;
    const rounds = await reloadConfig(roundsJobId);
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
    const coordinatorsBtn = event.target.closest(".coordinators-btn");
    if (editBtn) {
        const job = jobs.find(j => j.id === editBtn.dataset.id);
        openModal(job);
    } else if (deleteBtn) {
        deleteJob(deleteBtn.dataset.id);
    } else if (roundsBtn) {
        const job = jobs.find(function(item) { return item.id === roundsBtn.dataset.id; });
        if (job) openRoundsModal(job);
    } else if (coordinatorsBtn) {
        const job = jobs.find(function(item) { return item.id === coordinatorsBtn.dataset.id; });
        if (job) openCoordinatorsModal(job);
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

document.addEventListener("DOMContentLoaded", loadJobs);
