(function () {
    "use strict";

    function el(id) { return document.getElementById(id); }

    var jobsBody = el("jobsBody");
    var noJobs = el("noJobs");
    var jobModal = el("jobModal");
    var roundsModal = el("roundsModal");
    var applicantsModal = el("applicantsModal");
    var applicantsBody = el("applicantsBody");

    var jobs = [];
    var applicants = [];
    var editingJobId = null;
    var activeJobId = null;
    var activeApplicantsJobId = null;
    var activeRoundsJobId = null;

    function api(path, options) {
        options = options || {};
        options.credentials = "include";
        options.headers = options.headers || {};
        return fetch(path, options).then(function (res) {
            if (window.UI.redirectIfUnauthorized(res)) return Promise.reject(new Error("Unauthorized"));
            return res.text().then(function (text) {
                var data = null;
                try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
                if (!res.ok) {
                    var message = data && data.message ? data.message : ("Request failed (" + res.status + ")");
                    var err = new Error(message);
                    err.status = res.status;
                    throw err;
                }
                return data;
            });
        });
    }

    function statusLabel(status) {
        var value = String(status || "IN_PROGRESS").toUpperCase();
        if (value === "IN_PROGRESS") return "In Process";
        if (value === "SELECTED") return "Selected";
        if (value === "REJECTED") return "Rejected";
        if (value === "WITHDRAWN") return "Withdrawn";
        return status || "Applied";
    }

    function roundStatusLabel(status) {
        var value = String(status || "PENDING").toUpperCase();
        if (value === "QUALIFIED" || value === "CLEARED") return "Qualified";
        if (value === "REJECTED" || value === "NOT_CLEARED") return "Rejected";
        return "Pending";
    }

    function currentRoundEntry(app) {
        var current = Number(app.currentRound) || 1;
        var entry = (app.rounds || []).find(function (item) { return Number(item.round) === current; });
        return entry || { round: current, status: "PENDING" };
    }

    // ---------------------------------------------------------------- jobs

    function renderJobs() {
        if (!jobs.length) {
            jobsBody.innerHTML = "";
            noJobs.classList.remove("hidden");
            return;
        }
        noJobs.classList.add("hidden");
        var esc = window.UI.escapeHtml;
        jobsBody.innerHTML = jobs.map(function (job) {
            return "<tr>" +
                "<td>" + esc(job.company || "-") + "</td>" +
                "<td>" + esc(job.jobType || "-") + "</td>" +
                "<td>" + esc(job.location || "-") + "</td>" +
                "<td>" + esc(job.applicationDeadline || "-") + "</td>" +
                "<td>" + esc(job.status || "-") + "</td>" +
                "<td>" + (job.currentRound != null ? "Round " + esc(job.currentRound) : "—") + "</td>" +
                "<td>" + esc(job.applicantCount != null ? job.applicantCount : 0) + "</td>" +
                "<td>" + esc(job.selectedCount != null ? job.selectedCount : 0) + "</td>" +
                "<td>" + esc(job.pendingCount != null ? job.pendingCount : 0) + "</td>" +
                "<td>" +
                '<button class="btn btn-small" data-applicants-job="' + esc(job.id) + '">Applicants</button> ' +
                '<button class="btn btn-small btn-secondary" data-rounds-job="' + esc(job.id) + '">Rounds</button> ' +
                '<button class="btn btn-small btn-secondary" data-edit-job="' + esc(job.id) + '">Edit</button> ' +
                '<button class="btn btn-small btn-danger" data-delete-job="' + esc(job.id) + '">Delete</button>' +
                "</td>" +
                "</tr>";
        }).join("");
    }

    function renderJobStats() {
        var open = jobs.filter(function (job) { return String(job.status || "").toLowerCase() !== "closed"; }).length;
        var pending = jobs.reduce(function (sum, job) { return sum + (Number(job.pendingCount) || 0); }, 0);
        var selected = jobs.reduce(function (sum, job) { return sum + (Number(job.selectedCount) || 0); }, 0);
        el("statJobs").textContent = jobs.length;
        el("statOpen").textContent = open;
        el("statPending").textContent = pending;
        el("statSelected").textContent = selected;
    }

    function loadJobs() {
        jobsBody.innerHTML = '<tr><td colspan="10" style="text-align:center;color:var(--text-muted)">Loading jobs...</td></tr>';
        return api("/coordinator/jobs").then(function (data) {
            jobs = Array.isArray(data) ? data : [];
            renderJobs();
            renderJobStats();
            if (activeApplicantsJobId) {
                var current = jobs.find(function (job) { return job.id === activeApplicantsJobId; });
                if (current) {
                    activeJobId = current;
                    populateRoundOptions(activeJobId);
                } else {
                    window.UI.closeModal(applicantsModal);
                    activeApplicantsJobId = null;
                }
            }
        }).catch(function (err) {
            jobs = [];
            renderJobStats();
            jobsBody.innerHTML = '<tr><td colspan="10" style="text-align:center;color:var(--text-muted)">' +
                window.UI.escapeHtml(err.message || "Could not load jobs") + "</td></tr>";
        });
    }

    function jobById(id) {
        return jobs.find(function (item) { return item.id === id; }) || null;
    }

    function addAssessmentRound(round) {
        var row = document.createElement("div");
        row.className = "round-row assessment-round-row";
        row.innerHTML = '<input type="number" class="assessment-number" min="1" value="' +
            (round && round.round ? round.round : document.querySelectorAll(".assessment-round-row").length + 1) +
            '" aria-label="Round number">' +
            '<input list="roundTypes" class="assessment-type" placeholder="Round type" value="' +
            window.UI.escapeHtml(round && (round.roundType || round.name) || "") + '">' +
            '<input type="date" class="assessment-date" value="' + window.UI.escapeHtml(round && round.date || "") + '">' +
            '<input type="time" class="assessment-time" value="' + window.UI.escapeHtml(round && round.time || "") + '">' +
            '<input class="assessment-duration" placeholder="Duration" value="' + window.UI.escapeHtml(round && round.duration || "") + '">' +
            '<button type="button" class="round-remove-btn" aria-label="Remove round">×</button>';
        el("assessmentRounds").appendChild(row);
    }

    function collectAssessmentRounds() {
        return Array.prototype.slice.call(document.querySelectorAll(".assessment-round-row")).map(function (row) {
            var type = row.querySelector(".assessment-type").value.trim();
            return { round: Number(row.querySelector(".assessment-number").value), name: type,
                roundType: type, date: row.querySelector(".assessment-date").value,
                time: row.querySelector(".assessment-time").value,
                duration: row.querySelector(".assessment-duration").value.trim() };
        }).filter(function (round) { return round.round > 0 && round.name; });
    }

    function openJobModal(job) {
        editingJobId = job ? job.id : null;
        el("jobModalTitle").textContent = job ? "Edit Job Role" : "Add Job Role";
        el("saveJobBtn").textContent = job ? "Save Changes" : "Add Job Role";
        ["jobCompany", "jobType", "jobStipend", "jobProgramFee", "jobDuration", "jobSkills", "jobEligibility",
            "jobDeadline", "jobLink", "jobLocation", "jobOpenings", "jobBatch", "jobBranch", "jobDescription"]
            .forEach(function (id) { el(id).value = ""; });
        el("jobStatus").value = "Open";
        el("assessmentRounds").innerHTML = "";
        if (job) {
            el("jobCompany").value = job.company || "";
            el("jobType").value = job.jobType || "";
            el("jobLocation").value = job.location || "";
            el("jobDeadline").value = job.applicationDeadline || "";
            el("jobStatus").value = job.status || "Open";
            (job.rounds || []).forEach(function (round) { addAssessmentRound(round); });
        }
        window.UI.openModal(jobModal);
    }

    function saveJob() {
        var company = el("jobCompany").value.trim();
        var jobType = el("jobType").value.trim();
        if (!company || !jobType) { window.UI.showToast("Company and job role are required", "error"); return; }
        var button = el("saveJobBtn");
        button.disabled = true;
        var payload = {
            company: company, jobType: jobType, stipend: el("jobStipend").value.trim(),
            programFee: el("jobProgramFee").value.trim(), duration: el("jobDuration").value.trim(),
            skills: el("jobSkills").value.trim(), eligibility: el("jobEligibility").value.trim(),
            applicationDeadline: el("jobDeadline").value, applicationLink: el("jobLink").value.trim(),
            location: el("jobLocation").value.trim(), openings: el("jobOpenings").value.trim(),
            batch: el("jobBatch").value.trim(), branch: el("jobBranch").value.trim(),
            status: el("jobStatus").value, jobDescription: el("jobDescription").value.trim(),
            rounds: collectAssessmentRounds()
        };
        var request = editingJobId
            ? api("/coordinator/jobs/" + encodeURIComponent(editingJobId), { method: "PUT",
                headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) })
            : api("/coordinator/jobs", { method: "POST",
                headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
        request.then(function () {
            window.UI.closeModal(jobModal);
            window.UI.showToast(editingJobId ? "Job role updated" : "Job role added", "success");
            loadJobs();
        }).catch(function (err) {
            window.UI.showToast(err.message || "Could not save job role", "error");
        }).finally(function () { button.disabled = false; });
    }

    function deleteJob(id) {
        var job = jobById(id);
        window.UI.confirmAction("Delete " + ((job && job.company) || "this job") + " and all its applicants?",
            { title: "Delete job", danger: true, confirmText: "Delete" }).then(function (ok) {
            if (!ok) return;
            api("/coordinator/jobs/" + encodeURIComponent(id), { method: "DELETE" }).then(function () {
                window.UI.showToast("Job deleted", "success");
                loadJobs();
            }).catch(function (err) { window.UI.showToast(err.message || "Could not delete job", "error"); });
        });
    }

    // ---------------------------------------------------------------- rounds

    function populateRoundOptions(job) {
        var rounds = (job && job.rounds) || [];
        var options = rounds.map(function (round) {
            return '<option value="' + round.round + '">Round ' + round.round +
                (round.name ? " - " + window.UI.escapeHtml(round.name) : "") + "</option>";
        }).join("");
        el("applicantRoundFilter").innerHTML = '<option value="">All rounds</option>' + options;
        el("bulkRound").innerHTML = '<option value="">Target round</option>' + options;
    }

    function openRoundsModal(jobId) {
        activeRoundsJobId = jobId;
        var job = jobById(jobId);
        el("roundsJobInfo").innerHTML = "<p><strong>Job</strong> "
            + window.UI.escapeHtml(job ? (job.company || "-") + " — " + (job.jobType || "-") : jobId) + "</p>";
        el("newRoundNumber").value = "";
        el("newRoundName").value = "";
        el("newRoundDescription").value = "";
        window.UI.openModal(roundsModal);
        loadRounds();
    }

    function loadRounds() {
        el("roundsBody").innerHTML = '<tr><td colspan="4">Loading rounds...</td></tr>';
        api("/coordinator/jobs/" + encodeURIComponent(activeRoundsJobId) + "/rounds").then(function (list) {
            var rounds = Array.isArray(list) ? list : [];
            var esc = window.UI.escapeHtml;
            el("roundsBody").innerHTML = rounds.length ? rounds.map(function (round) {
                return "<tr><td>" + esc(round.roundNumber) + "</td><td>" + esc(round.roundName || "-") +
                    "</td><td>" + esc(round.description || "-") + "</td>" +
                    '<td><button class="btn btn-small btn-secondary" data-edit-round="' + esc(round.roundNumber) + '">Edit</button> ' +
                    '<button class="btn btn-small btn-danger" data-delete-round="' + esc(round.roundNumber) + '">Delete</button></td></tr>';
            }).join("") : '<tr><td colspan="4">No rounds configured yet.</td></tr>';
        }).catch(function (err) {
            el("roundsBody").innerHTML = '<tr><td colspan="4">' + window.UI.escapeHtml(err.message) + "</td></tr>";
        });
    }

    function addRound() {
        var number = Number(el("newRoundNumber").value);
        var name = el("newRoundName").value.trim();
        if (!number || number < 1) { window.UI.showToast("Round number must be a positive integer", "error"); return; }
        api("/coordinator/jobs/" + encodeURIComponent(activeRoundsJobId) + "/rounds", {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ roundNumber: number, roundName: name, description: el("newRoundDescription").value.trim() })
        }).then(function () {
            window.UI.showToast("Round added", "success");
            el("newRoundNumber").value = "";
            el("newRoundName").value = "";
            el("newRoundDescription").value = "";
            loadRounds();
            loadJobs();
        }).catch(function (err) { window.UI.showToast(err.message || "Could not add round", "error"); });
    }

    function editRound(number) {
        var name = window.prompt("Round name");
        if (name === null) return;
        api("/coordinator/jobs/" + encodeURIComponent(activeRoundsJobId) + "/rounds/" + number, {
            method: "PUT", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ roundName: name })
        }).then(function () {
            window.UI.showToast("Round updated", "success");
            loadRounds();
            loadJobs();
        }).catch(function (err) { window.UI.showToast(err.message || "Could not update round", "error"); });
    }

    function deleteRound(number) {
        window.UI.confirmAction("Delete Round " + number + "?", { title: "Delete round", danger: true, confirmText: "Delete" })
            .then(function (ok) {
                if (!ok) return;
                api("/coordinator/jobs/" + encodeURIComponent(activeRoundsJobId) + "/rounds/" + number, { method: "DELETE" })
                    .then(function () {
                        window.UI.showToast("Round deleted", "success");
                        loadRounds();
                        loadJobs();
                    }).catch(function (err) { window.UI.showToast(err.message || "Could not delete round", "error"); });
            });
    }

    // ---------------------------------------------------------------- applicants

    function loadApplicants(jobId) {
        var sameJob = activeApplicantsJobId === jobId;
        activeApplicantsJobId = jobId;
        activeJobId = jobById(jobId);
        if (!sameJob) {
            el("applicantSearch").value = "";
            el("applicantStatusFilter").value = "";
            el("applicantRoundFilter").value = "";
            el("applicantRoundStatusFilter").value = "";
            el("applicantRemark").value = "";
            el("bulkRound").value = "";
        }
        el("applicantsTitle").textContent = activeJobId
            ? "Applicants — " + (activeJobId.company || "") + " (" + (activeJobId.jobType || "") + ")" : "Applicants";
        populateRoundOptions(activeJobId);
        applicantsBody.innerHTML = '<tr><td colspan="6">Loading applicants...</td></tr>';
        window.UI.openModal(applicantsModal);
        api("/coordinator/jobs/" + encodeURIComponent(jobId) + "/applications").then(function (list) {
            applicants = Array.isArray(list) ? list : [];
            renderApplicants();
        }).catch(function (err) {
            applicants = [];
            applicantsBody.innerHTML = '<tr><td colspan="6">' + window.UI.escapeHtml(err.message) + "</td></tr>";
        });
    }

    function renderApplicants() {
        var search = el("applicantSearch").value.trim().toLowerCase();
        var status = el("applicantStatusFilter").value;
        var round = el("applicantRoundFilter").value;
        var roundStatus = el("applicantRoundStatusFilter").value;
        var esc = window.UI.escapeHtml;

        var filtered = applicants.filter(function (app) {
            if (status && String(app.status || "").toUpperCase() !== status) return false;
            var entry = currentRoundEntry(app);
            if (round && String(entry.round) !== round) return false;
            if (roundStatus) {
                var normalized = String(entry.status || "PENDING").toUpperCase();
                if (normalized === "CLEARED") normalized = "QUALIFIED";
                if (normalized === "NOT_CLEARED") normalized = "REJECTED";
                if (normalized !== roundStatus) return false;
            }
            if (search) {
                var haystack = [app.studentName, app.enrollmentno].join(" ").toLowerCase();
                if (haystack.indexOf(search) === -1) return false;
            }
            return true;
        });

        if (!filtered.length) {
            applicantsBody.innerHTML = '<tr><td colspan="6">No matching applicants.</td></tr>';
            return;
        }

        applicantsBody.innerHTML = filtered.map(function (app) {
            var entry = currentRoundEntry(app);
            var done = ["SELECTED", "REJECTED", "WITHDRAWN"].indexOf(String(app.status || "").toUpperCase()) !== -1;
            var actions = done ? "—" :
                '<button class="btn btn-small" data-qualify="' + esc(app.applicationId) + '" data-round="' + esc(entry.round) + '">Qualify</button> ' +
                '<button class="btn btn-small btn-secondary" data-reject="' + esc(app.applicationId) + '" data-round="' + esc(entry.round) + '">Reject</button> ' +
                '<button class="btn btn-small btn-secondary" data-advance="' + esc(app.applicationId) + '">Advance</button>';
            return "<tr>" +
                '<td><input type="checkbox" data-app-select="' + esc(app.applicationId) + '"' + (done ? " disabled" : "") + "></td>" +
                "<td>" + esc(app.studentName || app.enrollmentno || "Student") +
                (app.enrollmentno && app.studentName ? '<span class="selection-meta">' + esc(app.enrollmentno) + "</span>" : "") + "</td>" +
                "<td>" + esc(statusLabel(app.status)) + "</td>" +
                "<td>Round " + esc(entry.round) + "</td>" +
                "<td>" + esc(roundStatusLabel(entry.status)) + "</td>" +
                "<td>" + actions + "</td>" +
                "</tr>";
        }).join("");
        el("selectAllApplicants").checked = false;
    }

    function selectedApplicationIds() {
        return Array.prototype.slice.call(applicantsBody.querySelectorAll("input[data-app-select]:checked"))
            .map(function (box) { return box.getAttribute("data-app-select"); });
    }

    function rowAction(path, id) {
        api("/coordinator/applications/" + encodeURIComponent(id) + path, {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ adminRemark: el("applicantRemark").value.trim() })
        }).then(function () {
            window.UI.showToast("Application updated", "success");
            reloadApplicants();
        }).catch(function (err) { window.UI.showToast(err.message || "Could not update application", "error"); });
    }

    function bulkAction(path, payload) {
        var ids = selectedApplicationIds();
        if (!ids.length) { window.UI.showToast("Select at least one applicant", "error"); return; }
        payload.applicationIds = ids;
        var remark = el("applicantRemark").value.trim();
        if (remark) payload.adminRemark = remark;
        api("/coordinator/applications/bulk/" + path, {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload)
        }).then(function (result) {
            var failed = result && result.failed ? result.failed : 0;
            window.UI.showToast(failed ? "Updated with " + failed + " failure(s)" : "Applicants updated", failed ? "warning" : "success");
            reloadApplicants();
        }).catch(function (err) { window.UI.showToast(err.message || "Could not update applicants", "error"); });
    }

    function reloadApplicants() {
        if (!activeApplicantsJobId) return;
        api("/coordinator/jobs/" + encodeURIComponent(activeApplicantsJobId) + "/applications").then(function (list) {
            applicants = Array.isArray(list) ? list : [];
            renderApplicants();
            loadJobs();
        });
    }

    // ---------------------------------------------------------------- assigned students

    function buildCombos(lists) {
        var branches = Array.isArray(lists.branches) ? lists.branches : [];
        var sections = Array.isArray(lists.sections) ? lists.sections : [];
        var batches = Array.isArray(lists.batches) ? lists.batches : [];
        var length = Math.max(branches.length, sections.length, batches.length);
        var combos = [];
        for (var i = 0; i < length; i++) {
            combos.push({ branch: branches[i], section: sections[i], batch: batches[i] });
        }
        return combos;
    }

    function renderBreakdown(combos) {
        var grid = el("breakdownGrid");
        if (!combos.length) {
            grid.innerHTML = '<div class="stat-card"><strong>—</strong><span>No students assigned yet</span></div>';
            return;
        }
        grid.innerHTML = combos.map(function (combo) {
            var label = combo.branch ? combo.branch : "All";
            return '<div class="stat-card"><strong>' +
                window.UI.escapeHtml(label) + "</strong><span>" +
                window.UI.escapeHtml(label + " · " + (combo.section || "—") + " · " + (combo.batch || "—")) +
                "</span></div>";
        }).join("");
    }

    function renderCriteria(combos) {
        var body = el("criteriaBody");
        var empty = el("noBreakdown");
        if (!combos.length) {
            body.innerHTML = "";
            empty.classList.remove("hidden");
            return;
        }
        empty.classList.add("hidden");
        body.innerHTML = combos.map(function (combo) {
            return "<tr>" +
                "<td>" + window.UI.escapeHtml(combo.branch || "—") + "</td>" +
                "<td>" + window.UI.escapeHtml(combo.section || "—") + "</td>" +
                "<td>" + window.UI.escapeHtml(combo.batch || "—") + "</td>" +
                '<td><a class="btn btn-small btn-secondary" href="coordinator-students.html?branch=' +
                encodeURIComponent(combo.branch || "") + "&section=" + encodeURIComponent(combo.section || "") +
                "&batch=" + encodeURIComponent(combo.batch || "") + '">View Students</a></td>' +
                "</tr>";
        }).join("");
    }

    function loadProfile() {
        api("/coordinator/profile").then(function (profile) {
            if (profile && (profile.name || profile.enrollmentno)) {
                el("welcome").textContent = "Welcome, " + (profile.name || profile.enrollmentno) +
                    ". Manage the job roles you own, their rounds and applicants.";
            }
        }).catch(function () {});
    }

    function loadBreakdown() {
        api("/coordinator/stats").then(function (breakdown) {
            renderBreakdown(buildCombos(breakdown || {}));
            renderCriteria(buildCombos(breakdown || {}));
        }).catch(function (err) {
            window.UI.showToast(err.message || "Could not load student breakdown", "error");
        });
    }

    // ---------------------------------------------------------------- events

    jobsBody.addEventListener("click", function (event) {
        var applicantsButton = event.target.closest("[data-applicants-job]");
        if (applicantsButton) { loadApplicants(applicantsButton.getAttribute("data-applicants-job")); return; }
        var roundsButton = event.target.closest("[data-rounds-job]");
        if (roundsButton) { openRoundsModal(roundsButton.getAttribute("data-rounds-job")); return; }
        var editButton = event.target.closest("[data-edit-job]");
        if (editButton) { openJobModal(jobById(editButton.getAttribute("data-edit-job"))); return; }
        var deleteButton = event.target.closest("[data-delete-job]");
        if (deleteButton) { deleteJob(deleteButton.getAttribute("data-delete-job")); }
    });

    el("addJobBtn").addEventListener("click", function () { openJobModal(null); });
    el("cancelJobBtn").addEventListener("click", function () { window.UI.closeModal(jobModal); });
    el("saveJobBtn").addEventListener("click", saveJob);
    el("addAssessmentRoundBtn").addEventListener("click", function () { addAssessmentRound(); });
    el("assessmentRounds").addEventListener("click", function (event) {
        var remove = event.target.closest(".round-remove-btn");
        if (remove) remove.parentElement.remove();
    });

    el("closeRoundsBtn").addEventListener("click", function () { window.UI.closeModal(roundsModal); });
    el("addRoundBtn").addEventListener("click", addRound);
    el("roundsBody").addEventListener("click", function (event) {
        var edit = event.target.closest("[data-edit-round]");
        if (edit) { editRound(edit.getAttribute("data-edit-round")); return; }
        var remove = event.target.closest("[data-delete-round]");
        if (remove) deleteRound(remove.getAttribute("data-delete-round"));
    });

    el("closeApplicantsBtn").addEventListener("click", function () { window.UI.closeModal(applicantsModal); });
    ["applicantSearch", "applicantStatusFilter", "applicantRoundFilter", "applicantRoundStatusFilter"].forEach(function (id) {
        el(id).addEventListener("input", renderApplicants);
        el(id).addEventListener("change", renderApplicants);
    });
    el("selectAllApplicants").addEventListener("change", function (event) {
        var checked = event.target.checked;
        applicantsBody.querySelectorAll("input[data-app-select]:not(:disabled)").forEach(function (box) {
            box.checked = checked;
        });
    });
    applicantsBody.addEventListener("click", function (event) {
        var qualify = event.target.closest("[data-qualify]");
        if (qualify) {
            rowAction("/rounds/" + qualify.getAttribute("data-round") + "/qualify", qualify.getAttribute("data-qualify"));
            return;
        }
        var reject = event.target.closest("[data-reject]");
        if (reject) {
            rowAction("/rounds/" + reject.getAttribute("data-round") + "/reject", reject.getAttribute("data-reject"));
            return;
        }
        var advance = event.target.closest("[data-advance]");
        if (advance) rowAction("/advance", advance.getAttribute("data-advance"));
    });
    el("bulkAdvanceBtn").addEventListener("click", function () { bulkAction("advance", {}); });
    el("bulkRejectBtn").addEventListener("click", function () { bulkAction("reject", {}); });
    el("bulkSetRoundBtn").addEventListener("click", function () {
        var target = el("bulkRound").value;
        if (!target) { window.UI.showToast("Choose a target round", "error"); return; }
        bulkAction("set-round", { targetRound: Number(target) });
    });

    function load() {
        loadProfile();
        loadJobs();
        loadBreakdown();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", load);
    } else {
        load();
    }
})();
