(function () {
    "use strict";

    var UI = window.UI;
    var studentId = null;
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

    function loadStudent() {
        el("errorBox").classList.add("hidden");
        el("profileContent").classList.add("hidden");
        el("loadingBox").classList.remove("hidden");

        api("/admin/students/" + encodeURIComponent(studentId)).then(function (data) {
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
        personal += infoBox("Role", student.role);
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

    function formatDate(value) {
        return value ? window.UI.fmtDate(value) : "—";
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
            var resumeLink = "—";
            if (app.resumeUrl) {
                try {
                    var resumeUrl = new URL(app.resumeUrl);
                    if (resumeUrl.protocol === "https:" || resumeUrl.protocol === "http:") {
                        resumeLink = '<a class="btn btn-small btn-secondary" href="' + UI.escapeHtml(resumeUrl.href) + '" target="_blank" rel="noopener noreferrer">View resume</a>';
                    }
                } catch (e) {}
            }
            return "<tr>" +
                "<td>" + UI.escapeHtml(app.company || "—") + "</td>" +
                "<td>" + UI.escapeHtml(app.role || app.jobType || "—") + "</td>" +
                "<td>" + formatDate(app.applicationDate) + "</td>" +
                "<td>Round " + (app.currentRound || 1) + "</td>" +
                "<td>" + UI.escapeHtml(String(score)) + "</td>" +
                '<td><span class="badge ' + badgeClass + '">' + UI.escapeHtml(status) + "</span></td>" +
                "<td>" + resumeLink + "</td>" +
                '<td class="table-actions"><a class="btn btn-small btn-secondary" href="admin-applications.html">Open in Applications</a></td>' +
                "</tr>";
        }).join("");

        if (!visible.length) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-muted)">No matching applications.</td></tr>';
        }
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

    function openEdit() {
        el("eName").value = student.name || "";
        el("eEmail").value = student.email || "";
        el("ePhone").value = student.phoneno || "";
        el("eBatch").value = student.batch || "";
        el("eBranch").value = student.branch || "";
        el("eSection").value = student.section || "";
        el("eStatus").value = student.studentStatus || "Active";
        el("eCourse").value = student.course || "";
        el("eCollege").value = student.college || "";
        el("eCgpa").value = student.cgpa != null ? student.cgpa : "";
        el("eDob").value = student.dob || "";
        el("eGender").value = student.gender || "";
        UI.openModal(el("editStudentModal"));
    }

    function saveEdit() {
        var payload = {
            name: el("eName").value.trim(),
            email: el("eEmail").value.trim(),
            phoneno: el("ePhone").value.trim(),
            batch: el("eBatch").value.trim(),
            branch: el("eBranch").value.trim(),
            section: el("eSection").value.trim(),
            studentStatus: el("eStatus").value,
            course: el("eCourse").value.trim(),
            college: el("eCollege").value.trim(),
            dob: el("eDob").value.trim(),
            gender: el("eGender").value
        };
        var cgpa = parseFloat(el("eCgpa").value);
        if (!isNaN(cgpa)) payload.cgpa = cgpa;

        el("saveEditBtn").disabled = true;
        var original = el("saveEditBtn").textContent;
        el("saveEditBtn").textContent = "Saving...";
        api("/admin/students/" + encodeURIComponent(studentId), {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        }).then(function (updated) {
            UI.showToast("Student updated", "success");
            UI.closeModal(el("editStudentModal"));
            student = updated;
            student.applications = apps;
            renderProfile();
        }).catch(function (err) {
            UI.showToast(err.message || "Could not update student", "error");
        }).finally(function () {
            el("saveEditBtn").disabled = false;
            el("saveEditBtn").textContent = original;
        });
    }

    function exportStudent() {
        var url = "/admin/students/export";
        api(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ scope: "selected", studentIds: [studentId] })
        }).then(function () {
            UI.showToast("Student CSV generated. If it did not download, check your browser.", "success");
        }).catch(function (err) {
            UI.showToast(err.message || "Could not export student", "error");
        });
    }

    var EMAIL_COLUMNS = ["Name", "Email", "Phone", "Enrollment", "Branch", "Batch", "CGPA",
        "Student Status", "Applications", "Application Status", "Current Round"];

    function renderEmailColumns() {
        var options = EMAIL_COLUMNS;
        el("esColumns").innerHTML = options.map(function (col) {
            return '<label><input type="checkbox" value="' + UI.escapeHtml(col) + '" checked> ' + UI.escapeHtml(col) + "</label>";
        }).join("");
    }

    function sendEmail() {
        var recipient = el("esRecipient").value.trim();
        if (!recipient) {
            UI.showToast("Enter a recipient email address", "error");
            return;
        }
        var columns = Array.from(el("esColumns").querySelectorAll("input:checked")).map(function (box) { return box.value; });
        el("sendStudentEmailBtn").disabled = true;
        var original = el("sendStudentEmailBtn").textContent;
        el("sendStudentEmailBtn").textContent = "Sending...";
        api("/admin/students/email-csv", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ recipientEmail: recipient, scope: "selected", studentIds: [studentId], columns: columns })
        }).then(function (data) {
            UI.showToast("Emailed CSV to " + data.recipient, "success");
            UI.closeModal(el("emailStudentModal"));
        }).catch(function (err) {
            UI.showToast(err.message || "Could not send the email", "error");
        }).finally(function () {
            el("sendStudentEmailBtn").disabled = false;
            el("sendStudentEmailBtn").textContent = original;
        });
    }

    function init() {
        var params = new URLSearchParams(window.location.search);
        studentId = params.get("id");
        if (!studentId) {
            window.location.href = "admin-students.html";
            return;
        }

        var statusOptions = ["IN_PROGRESS", "SELECTED", "REJECTED", "WITHDRAWN", "Applied", "Under Review", "Shortlisted", "Interview Scheduled"];
        el("appStatusFilter").innerHTML = '<option value="">All statuses</option>' +
            statusOptions.map(function (s) { return '<option value="' + UI.escapeHtml(s) + '">' + UI.escapeHtml(s) + "</option>"; }).join("");

        el("editStudentBtn").addEventListener("click", openEdit);
        el("cancelEditBtn").addEventListener("click", function () { UI.closeModal(el("editStudentModal")); });
        el("saveEditBtn").addEventListener("click", saveEdit);
        el("exportStudentBtn").addEventListener("click", exportStudent);
        el("emailStudentBtn").addEventListener("click", function () {
            renderEmailColumns();
            el("esRecipient").value = "";
            UI.openModal(el("emailStudentModal"));
        });
        el("cancelEmailStudentBtn").addEventListener("click", function () { UI.closeModal(el("emailStudentModal")); });
        el("sendStudentEmailBtn").addEventListener("click", sendEmail);

        el("appSearch").addEventListener("input", function () {
            setTimeout(renderApplications, 300);
        });
        el("appStatusFilter").addEventListener("change", renderApplications);

        loadStudent();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();