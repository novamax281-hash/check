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
        department: document.getElementById("pDepartment"),
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
        state: document.getElementById("pState")
    };

    var currentRole = "";

    function isStudent(role) {
        var value = String(role || "").toLowerCase();
        return value !== "admin" && value !== "coordinator";
    }

    function renderDetails(user) {
        var role = user.role || "Student";
        var rows = [
            ["Enrollment Number:", user.enrollmentno || "-"],
            ["Role:", role]
        ];
        if (isStudent(role)) {
            rows.push(["Status:", user.studentStatus || "-"]);
            rows.push(["Branch", user.branch || "-"], ["Section", user.section || "-"], ["Batch", user.batch || "-"]);
            rows.push(["Course", user.course || "-"], ["College", user.college || "-"]);
        } else {
            rows.push(["Name:", user.name || "-"], ["Email:", user.email || "-"], ["Mobile:", user.phoneno || "-"]);
        }
        if (role.toLowerCase() === "coordinator") {
            rows.push(["Department", user.department || "-"]);
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
        fields.branch.value = user.branch || "";
        fields.section.value = user.section || "";
        fields.department.value = user.department || "";
        fields.course.value = user.course || "";
        fields.college.value = user.college || "";
        fields.dob.value = user.dob || "";
        fields.gender.value = user.gender || "";
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
        fields.enrollment.readOnly = true;
        fields.email.readOnly = !student ? false : true;
        fields.phone.readOnly = !student ? false : true;
        fields.batch.disabled = student;
        fields.tenth.readOnly = student;
        fields.twelfth.readOnly = student;
    }

    async function loadProfile() {
        try {
            const response = await fetch("/api/profile", { credentials: "include" });
            if (window.UI.redirectIfUnauthorized(response)) return;
            if (!response.ok) return;
            const user = await response.json();
            currentRole = user.role || "Student";

            document.getElementById("profileName").textContent = user.name || user.enrollmentno || "User";
            document.getElementById("profileMeta").textContent = currentRole + (user.branch ? " · " + user.branch : "");
            document.getElementById("profileAvatar").textContent =
                window.UI.initials(user.name || user.enrollmentno);
            renderDetails(user);
            populateForm(user);
        } catch (error) {
        }
    }

    function numeric(field) {
        return field.value !== "" ? Number(field.value) : undefined;
    }

    async function saveProfile(event) {
        event.preventDefault();
        const saveBtn = document.getElementById("saveProfileBtn");
        window.UI.setButtonLoading(saveBtn, true, "Saving...");

        const payload = { name: fields.name.value };
        if (isStudent(currentRole)) {
            payload.branch = fields.branch.value;
            payload.section = fields.section.value;
            payload.department = fields.department.value;
            payload.course = fields.course.value;
            payload.college = fields.college.value;
            payload.dob = fields.dob.value;
            payload.gender = fields.gender.value;
            payload.cgpa = numeric(fields.cgpa);
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
            payload.department = fields.department.value;
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
                loadProfile();
            } else {
                window.UI.showToast(result.message || "Could not update profile", "error");
            }
        } catch (error) {
            window.UI.showToast("Something went wrong. Please try again.", "error");
        } finally {
            window.UI.setButtonLoading(saveBtn, false);
        }
    }

    document.getElementById("profileForm").addEventListener("submit", saveProfile);

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", loadProfile);
    } else {
        loadProfile();
    }
})();