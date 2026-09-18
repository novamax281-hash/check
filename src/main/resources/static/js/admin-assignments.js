(function () {
    "use strict";

    var UI = window.UI;

    var params = new URLSearchParams(window.location.search);
    var preSelectedCoordinator = params.get("coordinatorId") || "";

    var state = {
        coordinators: [],
        options: { branches: [], sections: [], batches: [], students: [] },
        selected: new Set(),
        assignments: []
    };

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
                    var err = new Error(message);
                    err.status = res.status;
                    throw err;
                }
                return data;
            });
        });
    }

    function populateSelect(select, values, blankLabel) {
        var options = ['<option value="">' + (blankLabel || "All") + "</option>"].concat(
            (values || []).map(function (v) {
                return '<option value="' + UI.escapeHtml(v) + '">' + UI.escapeHtml(v) + "</option>";
            })
        ).join("");
        select.innerHTML = options;
    }

    function renderCoordinatorSelect() {
        var select = el("assignCoordinator");
        if (!state.coordinators.length) {
            select.innerHTML = '<option value="">No coordinators available</option>';
            el("assignHint").textContent = "Add a coordinator first on the Coordinators page.";
            return;
        }
        el("assignHint").textContent = "";
        select.innerHTML = '<option value="">Select a coordinator...</option>' + state.coordinators.map(function (c) {
            return '<option value="' + UI.escapeHtml(c.id) + '">' +
                UI.escapeHtml(c.name || c.enrollmentno) + (c.assignedCount ? " (" + c.assignedCount + " assigned)" : "") +
                "</option>";
        }).join("");
        if (preSelectedCoordinator) select.value = preSelectedCoordinator;
    }

    function renderMode() {
        var mode = el("assignMode").value;
        el("criteriaMode").style.display = mode === "criteria" ? "" : "none";
        el("manualMode").style.display = mode === "manual" ? "" : "none";
    }

    function renderStudentPicker() {
        var search = el("assignSearch").value.trim().toLowerCase();
        var students = state.options.students || [];
        var visible = search ? students.filter(function (s) {
            return String(s.name || "").toLowerCase().includes(search) ||
                String(s.enrollmentno || "").toLowerCase().includes(search);
        }) : students;

        var tbody = el("assignStudentsBody");
        if (!visible.length) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text-muted)">No students found.</td></tr>';
            return;
        }
        tbody.innerHTML = visible.map(function (s) {
            var checked = state.selected.has(s.id) ? "checked" : "";
            return "<tr>" +
                '<td><input type="checkbox" class="assign-check" data-id="' + UI.escapeHtml(s.id) + '"' + checked + "></td>" +
                '<td><div class="student-cell"><span class="student-avatar">' +
                UI.escapeHtml(UI.initials(s.name, s.enrollmentno)) + "</span>" +
                '<div><div class="student-name">' + UI.escapeHtml(s.name || "—") + "</div></div></div></td>" +
                "<td>" + UI.escapeHtml(s.enrollmentno || "—") + "</td>" +
                "<td>" + UI.escapeHtml(s.branch || "—") + "</td>" +
                "<td>" + UI.escapeHtml(s.section || "—") + "</td>" +
                "<td>" + UI.escapeHtml(s.batch || "—") + "</td>" +
                "</tr>";
        }).join("");

        tbody.querySelectorAll(".assign-check").forEach(function (box) {
            box.addEventListener("change", function () {
                var id = box.getAttribute("data-id");
                if (box.checked) state.selected.add(id);
                else state.selected.delete(id);
                updateAssignCount();
            });
        });
    }

    function updateAssignCount() {
        el("assignCount").textContent = state.selected.size + " selected";
    }

    function groupAssignments(assignments) {
        var groups = {};
        assignments.forEach(function (a) {
            (a.coordinators || []).forEach(function (c) {
                if (!groups[c.id]) {
                    groups[c.id] = { id: c.id, name: c.name || "Unknown", students: [] };
                }
                groups[c.id].students.push(a);
            });
        });
        return Object.keys(groups).map(function (id) { return groups[id]; });
    }

    function renderAssignments() {
        var container = el("assignmentsList");
        var emptyMsg = el("emptyMsg");
        var total = state.assignments.length;
        el("assignmentsCount").textContent = total + " assignment" + (total === 1 ? "" : "s");

        if (!total) {
            container.innerHTML = "";
            emptyMsg.classList.remove("hidden");
            return;
        }
        emptyMsg.classList.add("hidden");

        var groups = groupAssignments(state.assignments);
        var html = groups.map(function (group) {
            var rows = group.students.map(function (s) {
                return "<tr>" +
                    '<td><div class="student-cell"><span class="student-avatar">' +
                    UI.escapeHtml(UI.initials(s.name, s.enrollmentno)) + "</span>" +
                    '<div><div class="student-name">' + UI.escapeHtml(s.name || "—") + "</div>" +
                    '<div class="student-sub">' + UI.escapeHtml(s.enrollmentno || "") + "</div></div></div></td>" +
                    "<td>" + UI.escapeHtml(s.branch || "—") + "</td>" +
                    "<td>" + UI.escapeHtml(s.section || "—") + "</td>" +
                    "<td>" + UI.escapeHtml(s.batch || "—") + "</td>" +
                    '<td class="table-actions"><button type="button" class="btn btn-small btn-danger" data-unassign-rid="' +
                    UI.escapeHtml(group.id) + '" data-unassign-sid="' + UI.escapeHtml(s.studentId) +
                    '" data-unassign-name="' + UI.escapeHtml(s.name || "") + '">Unassign</button></td>' +
                    "</tr>";
            }).join("");
            return '<div class="mgmt-card" style="margin-bottom:14px;">' +
                '<div class="round-config-head"><h3>' + UI.escapeHtml(group.name) +
                ' <span class="badge">' + group.students.length + ' student' + (group.students.length === 1 ? "" : "s") +
                '</span></h3></div>' +
                '<div class="table-responsive"><table class="student-table"><thead><tr>' +
                '<th>Student</th><th>Branch</th><th>Section</th><th>Batch</th><th class="th-actions">Action</th>' +
                '</tr></thead><tbody>' + rows + "</tbody></table></div></div>";
        }).join("");
        container.innerHTML = html;

        container.querySelectorAll("[data-unassign-rid]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var coordinatorId = btn.getAttribute("data-unassign-rid");
                var studentId = btn.getAttribute("data-unassign-sid");
                var name = btn.getAttribute("data-unassign-name") || "this student";
                UI.confirmAction('Unassign "' + name + '"?', {
                    title: "Unassign Student", danger: true, confirmText: "Unassign", cancelText: "Cancel"
                }).then(function (ok) {
                    if (!ok) return;
                    api("/admin/assignments/" + encodeURIComponent(coordinatorId) + "/" + encodeURIComponent(studentId),
                        { method: "DELETE" })
                        .then(function (result) {
                            UI.showToast((result && result.message) || "Student unassigned", "success");
                            loadAssignments();
                            loadCoordinators();
                        }).catch(function (err) {
                            UI.showToast(err.message || "Could not unassign student", "error");
                        });
                });
            });
        });
    }

    function loadOptions() {
        api("/admin/assignment-options").then(function (data) {
            state.options = data || {};
            populateSelect(el("assignBranch"), state.options.branches || []);
            populateSelect(el("assignSection"), state.options.sections || []);
            populateSelect(el("assignBatch"), state.options.batches || []);
            renderStudentPicker();
        }).catch(function (err) {
            UI.showToast(err.message || "Could not load assignment options", "error");
        });
    }

    function loadCoordinators() {
        api("/admin/coordinators").then(function (list) {
            state.coordinators = list || [];
            renderCoordinatorSelect();
        }).catch(function (err) {
            UI.showToast(err.message || "Could not load coordinators", "error");
        });
    }

    function loadAssignments() {
        api("/admin/assignments").then(function (list) {
            state.assignments = list || [];
            renderAssignments();
        }).catch(function (err) {
            el("assignmentsList").innerHTML = '<div class="empty"><div class="empty-title">Error</div>' +
                '<p>' + UI.escapeHtml(err.message || "Could not load assignments") + "</p></div>";
        });
    }

    function assignStudents() {
        var coordinatorId = el("assignCoordinator").value;
        if (!coordinatorId) {
            UI.showToast("Please select a coordinator", "error");
            return;
        }
        var mode = el("assignMode").value;
        var payload = { coordinatorId: coordinatorId, branch: null, section: null, batch: null, studentIds: null };

        if (mode === "criteria") {
            payload.branch = el("assignBranch").value || null;
            payload.section = el("assignSection").value || null;
            payload.batch = el("assignBatch").value || null;
            if (!payload.branch && !payload.section && !payload.batch) {
                UI.showToast("Select at least one criterion (branch, section or batch)", "error");
                return;
            }
        } else {
            payload.studentIds = Array.from(state.selected);
            if (!payload.studentIds.length) {
                UI.showToast("Select at least one student", "error");
                return;
            }
        }

        var btn = el("assignBtn");
        btn.disabled = true;
        var original = btn.textContent;
        btn.textContent = "Assigning...";
        api("/admin/assign", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        }).then(function (result) {
            var details = "Assigned " + (result.assigned || 0) + " new";
            if (result.alreadyAssigned) details += ", " + result.alreadyAssigned + " already assigned";
            UI.showToast((result.message || "Assignment complete") + " — " + details, "success");
            state.selected.clear();
            updateAssignCount();
            renderStudentPicker();
            loadAssignments();
            loadCoordinators();
            loadOptions();
        }).catch(function (err) {
            UI.showToast(err.message || "Assignment failed", "error");
        }).finally(function () {
            btn.disabled = false;
            btn.textContent = original;
        });
    }

    function init() {
        el("assignMode").addEventListener("change", renderMode);
        el("assignSearch").addEventListener("input", function () {
            clearTimeout(this._t);
            var searchBox = this;
            this._t = setTimeout(function () { renderStudentPicker(); }, 300);
        });
        el("assignBranch").addEventListener("change", renderStudentPicker);
        el("assignSection").addEventListener("change", renderStudentPicker);
        el("assignBatch").addEventListener("change", renderStudentPicker);
        el("assignBtn").addEventListener("click", assignStudents);

        renderMode();
        loadOptions();
        loadCoordinators();
        loadAssignments();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();