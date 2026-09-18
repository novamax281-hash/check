(function () {
    "use strict";

    var UI = window.UI;

    function el(id) { return document.getElementById(id); }

    var editingId = null;

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
                    var err = new Error(message);
                    err.status = res.status;
                    throw err;
                }
                return data;
            });
        });
    }

    function statusBadge(status) {
        var value = String(status || "Active");
        var cls = value.toLowerCase() === "inactive" ? "Rejected" : "Selected";
        return '<span class="badge ' + cls + '">' + UI.escapeHtml(value) + "</span>";
    }

    function renderCoordinators(list) {
        var tbody = el("coordinatorsBody");
        var emptyMsg = el("emptyMsg");
        el("resultCount").textContent = list.length + " coordinator" + (list.length === 1 ? "" : "s");

        if (!list.length) {
            tbody.innerHTML = "";
            emptyMsg.classList.remove("hidden");
            return;
        }
        emptyMsg.classList.add("hidden");

        tbody.innerHTML = list.map(function (coord) {
            return "<tr>" +
                '<td><div class="student-cell"><span class="student-avatar">' +
                UI.escapeHtml(UI.initials(coord.name, coord.enrollmentno)) + "</span>" +
                '<div><div class="student-name">' + UI.escapeHtml(coord.name || "—") + "</div>" +
                '<div class="student-sub">' + UI.escapeHtml(coord.enrollmentno || "") + "</div></div></div></td>" +
                "<td>" + UI.escapeHtml(coord.email || "—") + "</td>" +
                "<td>" + UI.escapeHtml(coord.enrollmentno || "—") + "</td>" +
                "<td>" + UI.escapeHtml(coord.phoneno || "—") + "</td>" +
                "<td>" + UI.escapeHtml(coord.department || "—") + "</td>" +
                "<td>" + statusBadge(coord.status) + "</td>" +
                "<td>" + (coord.assignedCount || 0) + "</td>" +
                '<td class="table-actions">' +
                '<button type="button" class="btn btn-small" data-progress="' + UI.escapeHtml(coord.id) + '">Progress</button> ' +
                '<button type="button" class="btn btn-small btn-secondary" data-edit="' + UI.escapeHtml(coord.id) + '">Edit</button> ' +
                '<a class="btn btn-small btn-secondary" href="admin-assignments.html?coordinatorId=' +
                encodeURIComponent(coord.id) + '">Assign</a> ' +
                '<button type="button" class="btn btn-small btn-danger" data-remove="' +
                UI.escapeHtml(coord.id) + '" data-name="' + UI.escapeHtml(coord.name || coord.enrollmentno || "") +
                '">Remove</button></td>' +
                "</tr>";
        }).join("");

        tbody.querySelectorAll("[data-edit]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var coord = list.find(function (item) { return item.id === btn.getAttribute("data-edit"); });
                if (coord) openCoordinatorModal(coord);
            });
        });

        tbody.querySelectorAll("[data-progress]").forEach(function (btn) {
            btn.addEventListener("click", function () { viewProgress(btn.getAttribute("data-progress")); });
        });

        tbody.querySelectorAll("[data-remove]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var id = btn.getAttribute("data-remove");
                var name = btn.getAttribute("data-name") || "this coordinator";
                UI.confirmAction('Removing "' + name + '" will also unassign all of their students. Continue?',
                    { title: "Remove Coordinator", danger: true, confirmText: "Remove", cancelText: "Cancel" })
                    .then(function (ok) {
                        if (!ok) return;
                        api("/admin/coordinators/" + encodeURIComponent(id), { method: "DELETE" })
                            .then(function (result) {
                                UI.showToast((result && result.message) || "Coordinator removed", "success");
                                load();
                            }).catch(function (err) {
                                UI.showToast(err.message || "Could not remove coordinator", "error");
                            });
                    });
            });
        });
    }

    function load() {
        api("/admin/coordinators").then(function (list) {
            renderCoordinators(list || []);
        }).catch(function (err) {
            el("coordinatorsBody").innerHTML = '<tr><td colspan="8" style="text-align:center;padding:24px;color:var(--text-muted)">' +
                UI.escapeHtml(err.message || "Could not load coordinators") + "</td></tr>";
        });
    }

    function openCoordinatorModal(coord) {
        editingId = coord ? coord.id : null;
        el("coordinatorModalTitle").textContent = coord ? "Edit Coordinator" : "Add Coordinator";
        el("saveCoordinatorBtn").textContent = coord ? "Save Changes" : "Save Coordinator";
        el("cEnrollment").value = coord ? (coord.enrollmentno || "") : "";
        el("cEnrollment").disabled = !!coord;
        el("cName").value = coord ? (coord.name || "") : "";
        el("cEmail").value = coord ? (coord.email || "") : "";
        el("cPhone").value = coord ? (coord.phoneno || "") : "";
        el("cDepartment").value = coord ? (coord.department || "") : "";
        el("cStatus").value = (coord && coord.status) || "Active";
        el("cStatusGroup").classList.toggle("hidden", !coord);
        UI.openModal(el("coordinatorModal"));
    }

    function saveCoordinator() {
        var button = el("saveCoordinatorBtn");
        var payload = {
            name: el("cName").value.trim(),
            email: el("cEmail").value.trim(),
            phoneno: el("cPhone").value.trim(),
            department: el("cDepartment").value.trim()
        };
        if (editingId) {
            payload.studentStatus = el("cStatus").value;
            if (!payload.name || !payload.email || !payload.phoneno) {
                UI.showToast("Name, email and mobile number are required", "error");
                return;
            }
            button.disabled = true;
            api("/admin/coordinators/" + encodeURIComponent(editingId), {
                method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload)
            }).then(function () {
                UI.showToast("Coordinator updated", "success");
                UI.closeModal(el("coordinatorModal"));
                load();
            }).catch(function (err) {
                UI.showToast(err.message || "Could not update coordinator", "error");
            }).finally(function () { button.disabled = false; });
            return;
        }

        payload.enrollmentno = el("cEnrollment").value.trim();
        if (!payload.enrollmentno || !payload.name || !payload.email || !payload.phoneno) {
            UI.showToast("Enrollment number, name, email and mobile number are required", "error");
            return;
        }
        button.disabled = true;
        api("/admin/coordinators", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload)
        }).then(function (data) {
            UI.showToast("Coordinator " + (data.name || data.enrollmentno) + " created", "success");
            UI.closeModal(el("coordinatorModal"));
            load();
        }).catch(function (err) {
            UI.showToast(err.message || "Could not create coordinator", "error");
        }).finally(function () { button.disabled = false; });
    }

    function viewProgress(id) {
        el("progressTitle").textContent = "Coordinator Progress";
        el("progressStats").innerHTML = '<div class="stat-card"><strong>…</strong><span>Loading</span></div>';
        el("progressRounds").innerHTML = "";
        UI.openModal(el("progressModal"));
        api("/admin/coordinators/" + encodeURIComponent(id) + "/progress").then(function (progress) {
            var coordinator = progress.coordinator || {};
            el("progressTitle").textContent = "Progress — " + (coordinator.name || coordinator.enrollmentno || "");
            var cards = [
                ["Total Jobs", progress.totalJobs],
                ["Active Jobs", progress.activeJobs],
                ["Completed Jobs", progress.completedJobs],
                ["Applications", progress.totalApplications],
                ["In Process", progress.pendingApplications],
                ["Selected", progress.selectedApplications],
                ["Rejected", progress.rejectedApplications]
            ];
            el("progressStats").innerHTML = cards.map(function (card) {
                return '<div class="stat-card"><strong>' + UI.escapeHtml(card[1] != null ? card[1] : 0) +
                    "</strong><span>" + UI.escapeHtml(card[0]) + "</span></div>";
            }).join("");
            var rounds = progress.roundWiseCounts || {};
            var keys = Object.keys(rounds);
            el("progressRounds").innerHTML = keys.length
                ? "<p><strong>Round-wise applicants</strong></p>" +
                    keys.map(function (key) {
                        return "<p><strong>" + UI.escapeHtml(key) + "</strong><span>" +
                            UI.escapeHtml(rounds[key]) + "</span></p>";
                    }).join("")
                : "<p><strong>Round-wise applicants</strong><span>No applicants yet</span></p>";
        }).catch(function (err) {
            el("progressStats").innerHTML = "";
            el("progressRounds").innerHTML = "<p>" + UI.escapeHtml(err.message || "Could not load progress") + "</p>";
        });
    }

    function init() {
        el("addCoordinatorBtn").addEventListener("click", function () { openCoordinatorModal(null); });
        el("cancelCoordinatorBtn").addEventListener("click", function () { UI.closeModal(el("coordinatorModal")); });
        el("saveCoordinatorBtn").addEventListener("click", saveCoordinator);
        el("closeProgressBtn").addEventListener("click", function () { UI.closeModal(el("progressModal")); });

        load();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
