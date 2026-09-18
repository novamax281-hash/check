(function () {
    "use strict";

    var esc = window.UI.escapeHtml;
    var fields = {
        name: document.getElementById("pName"),
        email: document.getElementById("pEmail"),
        phone: document.getElementById("pPhone"),
        department: document.getElementById("pDepartment"),
        dob: document.getElementById("pDob"),
        gender: document.getElementById("pGender"),
        address: document.getElementById("pAddress"),
        city: document.getElementById("pCity"),
        state: document.getElementById("pState")
    };

    var wraps = {
        department: document.getElementById("pDepartmentWrap"),
        dob: document.getElementById("pDobWrap"),
        gender: document.getElementById("pGenderWrap"),
        address: document.getElementById("pAddressWrap"),
        city: document.getElementById("pCityWrap"),
        state: document.getElementById("pStateWrap")
    };

    var currentRole = "";

    function isStudent(role) {
        var value = String(role || "").toLowerCase();
        return value !== "admin" && value !== "coordinator";
    }

    function showEditable(role) {
    var value = String(role || "").toLowerCase();
    var isCoordinator = value === "coordinator";
    var isAdmin = value === "admin";

    Object.keys(wraps).forEach(function (key) {
        var visible = !isCoordinator && !isAdmin && !(key === "department");
        wraps[key].classList.toggle("hidden", !visible);
    });
    if (isCoordinator) {
        wraps.department.classList.remove("hidden");
    }
}

    function renderDetails(user) {
        var role = user.role || "Student";
        var rows = [
            ["Enrollment Number", user.enrollmentno || "-"],
            ["Role", role],
            ["Status", user.studentStatus || "-"]
        ];
        if (isStudent(role)) {
            rows.push(["Branch", user.branch || "-"], ["Section", user.section || "-"], ["Batch", user.batch || "-"]);
            rows.push(["Course", user.course || "-"], ["College", user.college || "-"]);
        }
        if (role.toLowerCase() === "coordinator") {
            rows.push(["Department", user.department || "-"]);
        }
        document.getElementById("profileDetails").innerHTML = rows.map(function (row) {
            return "<p><strong>" + esc(row[0]) + "</strong><span>" + esc(row[1]) + "</span></p>";
        }).join("");
    }

    function populateForm(user) {
        fields.name.value = user.name || "";
        fields.email.value = user.email || "";
        fields.phone.value = user.phoneno || "";
        fields.department.value = user.department || "";
        fields.dob.value = user.dob || "";
        fields.gender.value = user.gender || "";
        fields.address.value = user.address || "";
        fields.city.value = user.city || "";
        fields.state.value = user.state || "";
        showEditable(user.role);
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
            // profile load failed silently
        }
    }

    async function saveProfile(event) {
        event.preventDefault();
        const saveBtn = document.getElementById("saveProfileBtn");
        window.UI.setButtonLoading(saveBtn, true, "Saving...");

        const payload = {
            name: fields.name.value,
            email: fields.email.value,
            phoneno: fields.phone.value
        };
        if (currentRole.toLowerCase() === "coordinator") {
            payload.department = fields.department.value;
        } else if (isStudent(currentRole)) {
            payload.dob = fields.dob.value;
            payload.gender = fields.gender.value;
            payload.address = fields.address.value;
            payload.city = fields.city.value;
            payload.state = fields.state.value;
        }

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