(function () {
    "use strict";

    var UI = window.UI;
    var params = new URLSearchParams(window.location.search);
    var studentId = params.get("id");
    var student = null;
    var apps = [];

    function el(id) { return document.getElementById(id); }

    function api(path, options) {
        options = options || {};
        options.credentials = "include";
        options.headers = options.headers || {};
        return fetch(path, options).then(function (res) {
            if (UI.redirectIfUnauthorized(res)) return Promise.reject(new Error("Unauthorized"));
            return res.text().then(function (text) {
                var data = null;
                try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
                if (!res.ok) {
                    var message = data && data.message ? data.message : ("Request failed (" + res.status + ")");
                    throw new Error(message);
                }
                return data;
            });
        });
    }

    function infoBox(label, value) {
        return '<div class="info-box"><div class="info-label">' + UI.escapeHtml(label) + '</div>' +
            '<div class="info-value">' + UI.escapeHtml(value == null || value === "" ? "—" : value) + "</div></div>";
    }

    function formatDate(value) {
        if (!value) return "—";
        var date = new Date(value);
        if (isNaN(date.getTime())) return value;
        return date.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
    }

    function loadStudent() {
        el("errorBox").classList.add("hidden");
        el("profileContent").classList.add("hidden");
        el("loadingBox").classList.remove("hidden");

        api("/coordinator/students/" + encodeURIComponent(studentId)).then(function (data) {
            student = data;
            apps = data.applications || [];
            renderProfile();
            renderTabs();
            el("loadingBox").classList.add("hidden");
            el("profileContent").classList.remove("hidden");
        }).catch(function (err) {
            el("loadingBox").classList.add("hidden");
            el("errorBox").classList.remove("hidden");
            el("errorText").textContent = err.message || "Could not load this student.";
        });
    }

    function renderProfile() {
        el("profileAvatar").textContent = UI.initials(student.name, student.enrollmentno);
        el("profileName").textContent = student.name || student.enrollmentno || "Unnamed";
        el("profileEmail").textContent = student.email || "No email";
        el("profileEnrollment").textContent = "Enrollment: " + (student.enrollmentno || "—") +
            " · " + (student.branch || "No branch") + " · " + (student.section || "No section") +
            " · " + (student.batch || "No batch");

        var personal = "";
        personal += infoBox("Full Name", student.name);
        personal += infoBox("Email", student.email);
        personal += infoBox("Phone", student.phoneno);
        personal += infoBox("Date of Birth", student.dob);
        personal += infoBox("Gender", student.gender);
        personal += infoBox("Address", student.address);
        personal += infoBox("City", student.city);
        personal += infoBox("State", student.state);
        personal += infoBox("Student Status", student.studentStatus);
        el("personalGrid").innerHTML = personal;

        var academic = "";
        academic += infoBox("Enrollment Number", student.enrollmentno);
        academic += infoBox("Course", student.course);
        academic += infoBox("Branch", student.branch);
        academic += infoBox("Section", student.section);
        academic += infoBox("Batch", student.batch);
        academic += infoBox("College", student.college);
        academic += infoBox("CGPA", student.cgpa);
        academic += infoBox("10th Percentage", student.tenthPercentage);
        academic += infoBox("12th Percentage", student.twelfthPercentage);
        academic += infoBox("Graduation Percentage", student.graduationPercentage);
        el("academicGrid").innerHTML = academic;
    }

    function renderTabs() {
        renderApplications();
        renderRounds();

        el("profileTabs").querySelectorAll(".tab-btn").forEach(function (btn) {
            btn.addEventListener("click", function () {
                el("profileTabs").querySelectorAll(".tab-btn").forEach(function (b) {
                    b.classList.toggle("active", b === btn);
                });
                var name = "tab-" + btn.getAttribute("data-tab");
                document.querySelectorAll(".tab-panel").forEach(function (panel) {
                    panel.classList.toggle("active", panel.id === name);
                });
            });
        });
    }

    function renderApplications() {
        var tbody = el("appsBody");
        el("appResultCount").textContent = apps.length + " application" + (apps.length === 1 ? "" : "s");

        if (!apps.length) {
            tbody.innerHTML = "";
            el("noAppsMsg").classList.remove("hidden");
            return;
        }
        el("noAppsMsg").classList.add("hidden");

        var searchTerm = el("appSearch").value.trim().toLowerCase();
        var statusFilter = el("appStatusFilter").value;
        var statuses = apps.map(function (app) { return String(app.status || "IN_PROGRESS"); })
            .filter(function (v, i, arr) { return arr.indexOf(v) === i; }).sort();
        el("appStatusFilter").innerHTML = '<option value="">All statuses</option>' + statuses.map(function (s) {
            return '<option value="' + UI.escapeHtml(s) + '">' + UI.escapeHtml(s) + "</option>";
        }).join("");
        el("appStatusFilter").value = statusFilter;

        var visible = apps.filter(function (app) {
            if (statusFilter && String(app.status || "").toLowerCase() !== statusFilter.toLowerCase()) return false;
            if (searchTerm) {
                var haystack = (app.company || "") + " " + (app.role || "") + " " + (app.jobType || "");
                if (!haystack.toLowerCase().includes(searchTerm)) return false;
            }
            return true;
        });

        tbody.innerHTML = visible.map(function (app) {
            var score = "";
            if (app.rounds && app.currentRound) {
                var current = app.rounds.filter(function (r) { return r.round === app.currentRound; })[0];
                score = current && current.score != null ? current.score : "—";
            }
            var status = app.status || "IN_PROGRESS";
            var badgeClass = UI.badge(status);
            var actions = '<select class="app-status-control" data-status-id="' + UI.escapeHtml(app.applicationId) + '">' +
                ["IN_PROGRESS", "Applied", "Under Review", "Shortlisted", "Interview Scheduled", "Selected", "Rejected", "Withdrawn"]
                    .map(function (option) { return '<option' + (option.toLowerCase() === status.toLowerCase() ? " selected" : "") + '>' + option + "</option>"; }).join("") +
                '</select><button class="btn btn-small btn-success" data-app-action="advance" data-app-id="' + UI.escapeHtml(app.applicationId) + '">Advance</button>' +
                '<button class="btn btn-small btn-danger" data-app-action="reject" data-app-id="' + UI.escapeHtml(app.applicationId) + '">Reject</button>' +
                '<button class="btn btn-small btn-secondary" data-app-action="reset-round" data-app-id="' + UI.escapeHtml(app.applicationId) + '" data-round="' + (app.currentRound || 1) + '">Reset Round</button>';
            return "<tr>" +
                "<td>" + UI.escapeHtml(app.company || "—") + "</td>" +
                "<td>" + UI.escapeHtml(app.role || app.jobType || "—") + "</td>" +
                "<td>" + formatDate(app.applicationDate) + "</td>" +
                "<td>Round " + (app.currentRound || 1) + "</td>" +
                "<td>" + UI.escapeHtml(String(score)) + "</td>" +
                '<td><span class="badge ' + badgeClass + '">' + UI.escapeHtml(status) + "</span></td>" +
                '<td class="table-actions">' + actions + "</td>" +
                "</tr>";
        }).join("");

        if (!visible.length) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--text-muted)">No matching applications.</td></tr>';
        }
    }

    function updateApplication(id, path, body) {
        return api(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body || {}) })
            .then(function (result) {
                UI.showToast((result && result.message) || "Application updated", "success");
                loadStudent();
            }).catch(function (err) { UI.showToast(err.message || "Could not update application", "error"); });
    }

    function saveStatus(id, status) {
        return api("/coordinator/applications/" + encodeURIComponent(id) + "/status", {
            method: "PUT", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ status: status })
        }).then(function () { UI.showToast("Application status updated", "success"); loadStudent(); })
            .catch(function (err) { UI.showToast(err.message || "Could not update status", "error"); });
    }

    function bindApplicationControls() {
        el("appSearch").addEventListener("input", renderApplications);
        el("appStatusFilter").addEventListener("change", renderApplications);
        el("appsBody").addEventListener("change", function (event) {
            var select = event.target.closest("[data-status-id]");
            if (select) saveStatus(select.getAttribute("data-status-id"), select.value);
        });
        el("appsBody").addEventListener("click", function (event) {
            var button = event.target.closest("[data-app-action]");
            if (!button) return;
            var id = button.getAttribute("data-app-id");
            var action = button.getAttribute("data-app-action");
            if (action === "reset-round") updateApplication(id, "/coordinator/applications/bulk/set-round", {
                applicationIds: [id], targetRound: Number(button.getAttribute("data-round"))
            });
            else updateApplication(id, "/coordinator/applications/bulk/" + action, { applicationIds: [id] });
        });
    }

    function renderRounds() {
        var list = el("roundsList");
        var withRounds = apps.filter(function (app) {
            return app.rounds && app.rounds.length;
        });
        if (!withRounds.length) {
            list.innerHTML = "";
            el("noRoundsMsg").classList.remove("hidden");
            return;
        }
        el("noRoundsMsg").classList.add("hidden");

        list.innerHTML = withRounds.map(function (app) {
            var company = UI.escapeHtml(app.company || app.role || "Unknown company");
            var progress = UI.progressList(app);
            return '<div class="mgmt-card" style="margin-bottom:14px;">' +
                '<div class="round-config-head"><h3>' + company + "</h3>" +
                '<span class="badge ' + UI.badge(app.status) + '">' + UI.escapeHtml(app.status || "IN_PROGRESS") + "</span></div>" +
                progress + "</div>";
        }).join("");
    }

    if (!studentId) {
        document.addEventListener("DOMContentLoaded", function () {
            el("loadingBox").classList.add("hidden");
            el("errorBox").classList.remove("hidden");
            el("errorText").textContent = "No student selected.";
        });
    } else if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            bindApplicationControls();
            loadStudent();
        });
    } else {
        (function () {
            bindApplicationControls();
            loadStudent();
        })();
    }
})();