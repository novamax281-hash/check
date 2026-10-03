(function () {
    "use strict";

    var esc = window.UI.escapeHtml;
    var fields = {
        enrollment: document.getElementById("pEnrollment"),
        name: document.getElementById("pName"),
        email: document.getElementById("pEmail"),
        phone: document.getElementById("pPhone"),
        batch: document.getElementById("pBatch"),
        branch: document.getElementById("pBranch"),
        section: document.getElementById("pSection"),
        course: document.getElementById("pCourse"),
        college: document.getElementById("pCollege"),
        dob: document.getElementById("pDob"),
        gender: document.getElementById("pGender"),
        cgpa: document.getElementById("pCgpa"),
        tenth: document.getElementById("pTenth"),
        twelfth: document.getElementById("pTwelfth"),
        grad: document.getElementById("pGrad"),
        address: document.getElementById("pAddress"),
        city: document.getElementById("pCity"),
        state: document.getElementById("pState"),
        sgpaList: document.getElementById("pSgpaList"),
        addSemesterBtn: document.getElementById("addSemesterBtn"),
        sgpaError: document.getElementById("sgpaError")
    };

    /* Single source of truth for the semester SGPA rows. Each entry owns its
       semester number, so the UI and the saved payload stay in sync and nothing
       is duplicated. Mirrors the profile's `sgpas` array on the server. */
    var semesterSGPA = [];
    var SGPA_MIN = 0;
    var SGPA_MAX = 10;
    var MAX_SEMESTERS = 12;

    function nextSemesterNumber() {
        return semesterSGPA.length + 1;
    }

    function addSemester(sgpa) {
        if (semesterSGPA.length >= MAX_SEMESTERS) return false;
        semesterSGPA.push({ semester: nextSemesterNumber(), sgpa: sgpa == null ? "" : String(sgpa) });
        renderSemesters();
        return true;
    }

    function removeSemester(semesterNumber) {
        var index = semesterSGPA.findIndex(function (entry) { return entry.semester === semesterNumber; });
        if (index === -1) return;
        semesterSGPA.splice(index, 1);
        renumberSemesters();
        renderSemesters();
    }

    function renumberSemesters() {
        semesterSGPA.forEach(function (entry, index) {
            entry.semester = index + 1;
        });
    }

    function validateSemesterSgpa() {
        var message = "";
        semesterSGPA.forEach(function (entry) {
            if (message) return;
            var raw = String(entry.sgpa).trim();
            if (raw === "") return;
            var value = Number(raw);
            if (!isFinite(value) || isNaN(value)) {
                message = "Semester " + entry.semester + ": enter a number between " + SGPA_MIN + " and " + SGPA_MAX + ".";
            } else if (value < SGPA_MIN || value > SGPA_MAX) {
                message = "Semester " + entry.semester + ": SGPA must be between " + SGPA_MIN + " and " + SGPA_MAX + ".";
            } else if (!/^\d+(\.\d{1,2})?$/.test(raw)) {
                message = "Semester " + entry.semester + ": use at most 2 decimal places.";
            }
        });
        return message;
    }

    function setSgpaError(message) {
        if (!fields.sgpaError) return;
        fields.sgpaError.textContent = message;
        fields.sgpaError.hidden = !message;
    }

    function renderSemesters() {
        if (!fields.sgpaList) return;
        fields.sgpaList.innerHTML = "";
        if (semesterSGPA.length === 0) {
            var empty = document.createElement("p");
            empty.className = "sgpa-empty";
            empty.textContent = "No semesters added yet.";
            fields.sgpaList.appendChild(empty);
        }
        semesterSGPA.forEach(function (entry) {
            var row = document.createElement("div");
            row.className = "sgpa-row";

            var label = document.createElement("label");
            label.className = "sgpa-row-label";
            label.textContent = "Semester " + entry.semester;

            var input = document.createElement("input");
            input.type = "number";
            input.min = String(SGPA_MIN);
            input.max = String(SGPA_MAX);
            input.step = "0.01";
            input.placeholder = "SGPA";
            input.value = entry.sgpa;
            input.setAttribute("aria-label", "SGPA for semester " + entry.semester);
            input.addEventListener("input", function () {
                entry.sgpa = input.value;
                setSgpaError("");
                updateComputedCgpa();
            });

            var remove = document.createElement("button");
            remove.type = "button";
            remove.className = "sgpa-remove";
            remove.title = "Remove semester " + entry.semester;
            remove.setAttribute("aria-label", "Remove semester " + entry.semester);
            remove.innerHTML = "&times;";
            remove.addEventListener("click", function () {
                removeSemester(entry.semester);
            });

            row.appendChild(label);
            row.appendChild(input);
            row.appendChild(remove);
            fields.sgpaList.appendChild(row);
        });
        if (fields.addSemesterBtn) {
            fields.addSemesterBtn.disabled = semesterSGPA.length >= MAX_SEMESTERS;
        }
    }

    function computedCgpa() {
        var sum = 0;
        var counted = 0;
        semesterSGPA.forEach(function (entry) {
            var raw = String(entry.sgpa).trim();
            if (raw === "") return;
            var value = Number(raw);
            if (isFinite(value) && !isNaN(value)) {
                sum += value;
                counted++;
            }
        });
        if (counted === 0) return "";
        return (Math.round((sum / counted) * 100) / 100).toFixed(2);
    }

    function updateComputedCgpa() {
        if (fields.cgpa) fields.cgpa.value = computedCgpa();
    }

    /* Loads the profile's stored `sgpas` into the array, dropping trailing blanks
       so the student only sees the semesters that actually have marks. */
    function loadSemesters(sgpas) {
        semesterSGPA.length = 0;
        var values = Array.isArray(sgpas) ? sgpas : [];
        var lastFilled = -1;
        values.forEach(function (value, index) {
            if (value !== null && value !== undefined && String(value).trim() !== "") lastFilled = index;
        });
        for (var i = 0; i <= lastFilled && i < MAX_SEMESTERS; i++) {
            var value = values[i];
            semesterSGPA.push({
                semester: i + 1,
                sgpa: value === null || value === undefined ? "" : String(value)
            });
        }
        setSgpaError("");
        renderSemesters();
        updateComputedCgpa();
    }

    function semesterValues() {
        return semesterSGPA.map(function (entry) {
            var raw = String(entry.sgpa).trim();
            return raw === "" ? null : Number(raw);
        });
    }

    var currentRole = "";

    function isStudent(role) {
        var value = String(role || "").toLowerCase();
        return value !== "admin" && value !== "coordinator";
    }

    function hasBranch(course) {
        var value = String(course || "").trim().toUpperCase();
        return value !== "MCA" && value !== "BCA";
    }

    function renderDetails(user) {
        var role = user.role || "Student";
        var branchApplies = hasBranch(user.course);
        var rows = [
            ["Enrollment Number:", user.enrollmentno || "-"],
            ["Role:", role]
        ];
        if (isStudent(role)) {
            rows.push(["Status:", user.studentStatus || "-"]);
            if (branchApplies && user.branch) rows.push(["Branch", user.branch]);
            rows.push(["Section", user.section || "-"], ["Batch", user.batch || "-"]);
            rows.push(["Course", user.course || "-"], ["College", user.college || "-"]);
        } else {
            rows.push(["Name:", user.name || "-"], ["Email:", user.email || "-"], ["Mobile:", user.phoneno || "-"]);
        }
        document.getElementById("profileDetails").innerHTML = rows.map(function (row) {
            return "<p><strong>" + esc(row[0]) + "</strong><span>" + esc(row[1]) + "</span></p>";
        }).join("");
    }

    function populateForm(user) {
        var student = isStudent(user.role || "");
        fields.enrollment.value = user.enrollmentno || "";
        fields.name.value = user.name || "";
        fields.batch.innerHTML = window.UI.batchOptions(user.batch || "");
        fields.batch.value = user.batch || "";
        fields.section.value = user.section || "";
        fields.course.value = user.course || "";
        fields.college.value = user.college || "";
        fields.dob.value = user.dob || "";
        fields.gender.value = user.gender || "";
        loadSemesters(user.sgpas);
        fields.cgpa.value = user.cgpa != null ? user.cgpa : "";
        fields.tenth.value = user.tenthPercentage != null ? user.tenthPercentage : "";
        fields.twelfth.value = user.twelfthPercentage != null ? user.twelfthPercentage : "";
        fields.grad.value = user.graduationPercentage != null ? user.graduationPercentage : "";
        fields.address.value = user.address || "";
        fields.city.value = user.city || "";
        fields.state.value = user.state || "";
        fields.email.value = user.email || "";
        fields.phone.value = user.phoneno || "";
        document.getElementById("pStudentFields").style.display = student ? "block" : "none";
        var branchApplies = hasBranch(user.course);
        document.getElementById("pBranchGroup").style.display = student && branchApplies ? "" : "none";
        fields.branch.disabled = !student || !branchApplies;
        fields.branch.value = user.branch || "";
        fields.enrollment.readOnly = true;
        fields.email.readOnly = student;
        fields.phone.readOnly = student;
        fields.tenth.readOnly = student;
        fields.twelfth.readOnly = student;
        fields.batch.disabled = student;
        fields.course.readOnly = student;
    }

    async function loadProfile(force) {
        // Reuse the sidebar's cached /user/profile result instead of issuing a second
        // request for the same user via /api/profile. Passing force=true (after a save)
        // bypasses the cache so the form always reflects just-saved data.
        let user;
        try {
            user = await window.UI.getProfile(force);
        } catch (error) {
            return;
        }
        if (!user || typeof user !== "object") {
            // getProfile resolves to null on a 401 but does not expose the status. Probe
            // once with HEAD to preserve the previous redirect-to-login on an expired
            // session without adding requests in the happy path.
            try {
                const probe = await fetch("/api/profile", { method: "HEAD", credentials: "include" });
                if (probe.status === 401) window.location.href = "/login.html";
            } catch (ignored) {
            }
            return;
        }
        currentRole = user.role || "Student";

        document.getElementById("profileName").textContent = user.name || user.enrollmentno || "User";
        document.getElementById("profileMeta").textContent =
            currentRole + (hasBranch(user.course) && user.branch ? " · " + user.branch : "");
        document.getElementById("profileAvatar").textContent =
            window.UI.initials(user.name || user.enrollmentno);
        renderDetails(user);
        populateForm(user);
    }

    function numeric(field) {
        return field.value !== "" ? Number(field.value) : undefined;
    }

    function hasAnySgpa() {
        return semesterSGPA.some(function (entry) { return String(entry.sgpa).trim() !== ""; });
    }

    async function saveProfile(event) {
        event.preventDefault();
        const saveBtn = document.getElementById("saveProfileBtn");

        const sgpaProblem = validateSemesterSgpa();
        if (sgpaProblem) {
            setSgpaError(sgpaProblem);
            if (fields.sgpaList) fields.sgpaList.scrollIntoView({ behavior: "smooth", block: "center" });
            window.UI.showToast(sgpaProblem, "error");
            return;
        }
        setSgpaError("");

        window.UI.setButtonLoading(saveBtn, true, "Saving...");

        const payload = { name: fields.name.value };
        if (isStudent(currentRole)) {
            if (!fields.branch.disabled) payload.branch = fields.branch.value;
            payload.section = fields.section.value;
            payload.college = fields.college.value;
            payload.dob = fields.dob.value;
            payload.gender = fields.gender.value;
            // Only send SGPAs when at least one is filled, so an imported overall CGPA
            // is not wiped by a student who has not entered any SGPAs yet.
            if (hasAnySgpa()) {
                payload.sgpas = semesterValues();
                payload.cgpa = Number(computedCgpa());
            }
            payload.graduationPercentage = numeric(fields.grad);
            payload.address = fields.address.value;
            payload.city = fields.city.value;
            payload.state = fields.state.value;
        } else {
            payload.email = fields.email.value;
            payload.phoneno = fields.phone.value;
            payload.cgpa = numeric(fields.cgpa);
            payload.tenthPercentage = numeric(fields.tenth);
            payload.twelfthPercentage = numeric(fields.twelfth);
            payload.graduationPercentage = numeric(fields.grad);
            payload.batch = fields.batch.value;
            payload.branch = fields.branch.value;
            payload.section = fields.section.value;
            payload.course = fields.course.value;
            payload.college = fields.college.value;
        }
        Object.keys(payload).forEach(function (key) {
            if (payload[key] === undefined) {
                delete payload[key];
            }
        });

        try {
            const response = await fetch("/api/profile", {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify(payload)
            });
            const result = await response.json().catch(function () { return {}; });
            if (window.UI.redirectIfUnauthorized(response)) return;
            if (response.ok) {
                window.UI.showToast("Profile updated successfully", "success");
                loadProfile(true);
            } else {
                window.UI.showToast(result.message || "Could not update profile", "error");
            }
        } catch (error) {
            window.UI.showToast("Something went wrong. Please try again.", "error");
        } finally {
            window.UI.setButtonLoading(saveBtn, false);
        }
    }

    if (fields.addSemesterBtn) {
        fields.addSemesterBtn.addEventListener("click", function () {
            if (addSemester("")) {
                setSgpaError("");
                var rows = fields.sgpaList ? fields.sgpaList.querySelectorAll(".sgpa-row") : [];
                var last = rows[rows.length - 1];
                if (last) {
                    var input = last.querySelector("input");
                    if (input) input.focus();
                }
            }
        });
    }

    document.getElementById("profileForm").addEventListener("submit", saveProfile);

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", loadProfile);
    } else {
        loadProfile();
    }
})();