(function () {
    "use strict";

    var UI = window.UI;

    var state = {
        page: 1,
        pageSize: 25,
        total: 0,
        totalPages: 1,
        search: "",
        filters: {
            batch: "",
            branch: "",
            section: "",
            studentStatus: "",
            company: "",
            job: "",
            applicationStatus: "",
            round: "",
            appliedFrom: "",
            appliedTo: ""
        },
        students: [],
        selected: new Set(),
        filterOptions: {},
        importData: null,
        operation: "ADD",
        csvContent: null,
        importResult: null
    };

    var studentsRequestId = 0;
    var debounceTimer = null;

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

    function downloadCsv(path, body, fallbackName) {
        return fetch(path, {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        }).then(function (res) {
            if (UI.redirectIfUnauthorized(res)) throw new Error("Unauthorized");
            if (!res.ok) {
                return res.text().then(function (text) {
                    var message = "Request failed (" + res.status + ")";
                    try {
                        var data = JSON.parse(text);
                        if (data && data.message) message = data.message;
                    } catch (e) {}
                    throw new Error(message);
                });
            }
            var disposition = res.headers.get("Content-Disposition") || "";
            var match = disposition.match(/filename="?([^";]+)"?/);
            return res.blob().then(function (blob) {
                var link = document.createElement("a");
                link.href = URL.createObjectURL(blob);
                link.download = match ? match[1] : fallbackName;
                document.body.appendChild(link);
                link.click();
                link.remove();
                setTimeout(function () { URL.revokeObjectURL(link.href); }, 1000);
            });
        });
    }

    function activeFilterCount() {
        var count = 0;
        var filters = state.filters;
        for (var key in filters) {
            if (filters.hasOwnProperty(key) && filters[key]) count++;
        }
        if (state.search.trim()) count++;
        return count;
    }

    function buildQuery() {
        var params = new URLSearchParams();
        params.set("page", state.page);
        params.set("pageSize", state.pageSize);
        if (state.search.trim()) params.set("search", state.search.trim());
        var filters = state.filters;
        if (filters.batch) params.set("batch", filters.batch);
        if (filters.branch) params.set("branch", filters.branch);
        if (filters.section) params.set("section", filters.section);
        if (filters.studentStatus) params.set("studentStatus", filters.studentStatus);
        if (filters.company) params.set("company", filters.company);
        if (filters.job) params.set("job", filters.job);
        if (filters.applicationStatus) params.set("applicationStatus", filters.applicationStatus);
        if (filters.round) params.set("round", filters.round);
        if (filters.appliedFrom) params.set("appliedFrom", filters.appliedFrom);
        if (filters.appliedTo) params.set("appliedTo", filters.appliedTo);
        return params.toString();
    }

    function loadStats() {
        return api("/admin/students/stats").then(function (stats) {
            el("statTotal").textContent = formatNumber(stats.total);
            el("statActive").textContent = formatNumber(stats.active);
            el("statPlaced").textContent = formatNumber(stats.placed);
            el("statNotPlaced").textContent = formatNumber(stats.notPlaced);
        }).catch(function () {
            el("statTotal").textContent = "—";
            el("statActive").textContent = "—";
            el("statPlaced").textContent = "—";
            el("statNotPlaced").textContent = "—";
        });
    }

    function loadFilterOptions() {
        return api("/admin/students/filter-options").then(function (options) {
            state.filterOptions = options;
            populateSelect(el("batchFilter"), options.batches || []);
            populateSelect(el("branchFilter"), options.branches || []);
            populateSelect(el("sectionFilter"), options.sections || []);
            populateSelect(el("studentStatusFilter"), options.studentStatuses || []);
            populateSelect(el("companyFilter"), options.companies || []);
            populateSelect(el("jobFilter"), options.jobs || []);
            populateSelect(el("applicationStatusFilter"), options.applicationStatuses || []);
            populateSelect(el("roundFilter"), options.rounds || []);
        }).catch(function () {
            UI.showToast("Could not load filter options", "error");
        });
    }

    function populateSelect(select, values) {
        var current = select.value;
        select.innerHTML = '<option value="">' + (values === state.filterOptions.studentStatuses ? "All Statuses"
            : values === state.filterOptions.applicationStatuses ? "All Statuses"
            : values === state.filterOptions.rounds ? "All Rounds" : "All") + "</option>";
        values.forEach(function (value) {
            var option = document.createElement("option");
            option.value = value;
            option.textContent = value;
            select.appendChild(option);
        });
        if (current === "" || (Array.isArray(values) && values.indexOf(current) >= 0)) {
            select.value = current;
        }
    }

    function loadStudents() {
        var tbody = el("studentsBody");
        tbody.innerHTML = '<tr><td colspan="10" style="text-align:center;padding:24px;color:var(--text-muted)">Loading students...</td></tr>';
        el("resultCount").textContent = "Loading students...";

        // Debounced search plus filter changes can overlap; only the newest
        // request may write state, otherwise a slow earlier response wins.
        var requestId = ++studentsRequestId;
        api("/admin/students?" + buildQuery()).then(function (data) {
            if (requestId !== studentsRequestId) return;
            state.total = data.total;
            state.totalPages = data.totalPages || 1;
            state.students = data.students || [];
            if (data.stats) {
                el("statTotal").textContent = formatNumber(data.stats.total);
                el("statActive").textContent = formatNumber(data.stats.active);
                el("statPlaced").textContent = formatNumber(data.stats.placed);
                el("statNotPlaced").textContent = formatNumber(data.stats.notPlaced);
            }
            renderStudents();
            renderPagination();
            updateFilterCount();
            updateSelectionUI();
        }).catch(function (err) {
            if (requestId !== studentsRequestId) return;
            UI.showToast(err.message || "Could not load students", "error");
            tbody.innerHTML = '<tr><td colspan="10" style="text-align:center;padding:24px;color:var(--text-muted)">' +
                UI.escapeHtml(err.message || "Could not load students") + "</td></tr>";
        });
    }

    function renderStudents() {
        var tbody = el("studentsBody");
        var emptyMsg = el("emptyMsg");
        var search = state.search.trim();

        if (!state.students.length) {
            tbody.innerHTML = "";
            emptyMsg.style.display = "";
            el("emptyTitle").textContent = search ? ("No student found for '" + search + "'.") : "No students found.";
            el("emptySub").textContent = search ? "" : "Try adjusting your search or filters.";
            el("resultCount").textContent = "0 students found";
            el("selectAllCheckbox").checked = false;
            el("selectAllCheckbox").disabled = false;
            return;
        }
        emptyMsg.style.display = "none";

        tbody.innerHTML = state.students.map(function (student) {
            var checked = state.selected.has(student.id) ? "checked" : "";
            var statusLabel = student.studentStatus || "Active";
            var badgeClass = UI.badge(statusLabel);
            var detailLink = student.id
                ? '<a class="btn btn-small btn-secondary" href="admin-student-detail.html?id='
                    + encodeURIComponent(student.id) + '">View</a>'
                : '<span class="field-hint">Unavailable</span>';
            return "<tr>" +
                '<td><input type="checkbox" class="row-checkbox" data-id="' + UI.escapeHtml(student.id) + '"' + checked + "></td>" +
                '<td><div class="student-cell">' +
                 '<div><div class="student-name">' + UI.escapeHtml(student.name || "—") + "</div>" +
                '<div class="student-sub">' + UI.escapeHtml(student.enrollmentno || "") + "</div></div></div></td>" +
                "<td>" + UI.escapeHtml(student.email || "—") + "</td>" +
                "<td>" + UI.escapeHtml(student.enrollmentno || "—") + "</td>" +
                "<td>" + UI.escapeHtml(student.branch || "") + "</td>" +
                "<td>" + UI.escapeHtml(student.section || "—") + "</td>" +
                "<td>" + UI.escapeHtml(student.batch || "—") + "</td>" +
                "<td>" + (student.applicationCount || 0) + "</td>" +
                '<td><span class="badge ' + badgeClass + '">' + UI.escapeHtml(statusLabel) + "</span></td>" +
                '<td class="table-actions">' + detailLink + "</td>" +
                "</tr>";
        }).join("");

        tbody.querySelectorAll(".row-checkbox").forEach(function (box) {
            box.addEventListener("change", function () {
                var id = box.getAttribute("data-id");
                if (box.checked) state.selected.add(id);
                else state.selected.delete(id);
                updateSelectionUI();
            });
        });
        el("resultCount").textContent = formatNumber(state.total) + " students found";
        syncSelectAll();
    }

    function syncSelectAll() {
        var all = el("selectAllCheckbox");
        var pageIds = state.students.map(function (s) { return s.id; });
        var selectedOnPage = pageIds.filter(function (id) { return state.selected.has(id); }).length;
        all.checked = selectedOnPage > 0 && selectedOnPage === pageIds.length;
        all.indeterminate = selectedOnPage > 0 && selectedOnPage < pageIds.length;
    }

    function updateSelectionUI() {
        var bar = el("bulkBar");
        var count = state.selected.size;
        el("bulkCount").textContent = count + " student" + (count === 1 ? "" : "s") + " selected";
        if (count) {
            bar.classList.remove("hidden");
        } else {
            bar.classList.add("hidden");
        }
        syncSelectAll();
    }

    function renderPagination() {
        var pagination = el("pagination");
        if (state.totalPages <= 1) {
            pagination.innerHTML = "";
            return;
        }
        var pages = [];
        var start = Math.max(1, state.page - 2);
        var end = Math.min(state.totalPages, state.page + 2);
        if (start > 1) pages.push({ label: "1", value: 1 }, { label: "…", value: null });
        for (var i = start; i <= end; i++) pages.push({ label: String(i), value: i });
        if (end < state.totalPages) pages.push({ label: "…", value: null }, { label: String(state.totalPages), value: state.totalPages });

        pagination.innerHTML = '<button class="page-btn" data-page="' + (state.page - 1) + '"' + (state.page <= 1 ? " disabled" : "") + ">‹ Prev</button>" +
            pages.map(function (p) {
                if (p.value === null) return '<span class="page-ellipsis">' + p.label + "</span>";
                return '<button class="page-btn' + (p.value === state.page ? " active" : "") + '" data-page="' + p.value + '">' + p.label + "</button>";
            }).join("") +
            '<button class="page-btn" data-page="' + (state.page + 1) + '"' + (state.page >= state.totalPages ? " disabled" : "") + ">Next ›</button>";

        pagination.querySelectorAll("[data-page]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var page = parseInt(btn.getAttribute("data-page"), 10);
                if (isNaN(page) || page < 1 || page > state.totalPages) return;
                state.page = page;
                loadStudents();
            });
        });
    }

    function formatNumber(value) {
        return Number(value || 0).toLocaleString("en-IN");
    }

    function updateFilterCount() {
        var count = activeFilterCount();
        var badge = el("filterCountBadge");
        if (count > 0) {
            badge.textContent = count;
            badge.classList.remove("hidden");
        } else {
            badge.classList.add("hidden");
        }
    }

    

    function syncFilterControls() {
        var f = state.filters;
        el("batchFilter").value = f.batch;
        el("branchFilter").value = f.branch;
        el("sectionFilter").value = f.section;
        el("studentStatusFilter").value = f.studentStatus;
        el("companyFilter").value = f.company;
        el("jobFilter").value = f.job;
        el("applicationStatusFilter").value = f.applicationStatus;
        el("roundFilter").value = f.round;
        el("appliedFromFilter").value = f.appliedFrom;
        el("appliedToFilter").value = f.appliedTo;
        updateFilterCount();
    }

    

    function bulkStatus() {
        if (!state.selected.size) return;
        el("bulkStatusCount").textContent = state.selected.size;
        UI.openModal(el("bulkStatusModal"));
    }

    function bulkRound() {
        if (!state.selected.size) return;
        el("bulkRoundInput").value = "1";
        el("bulkRoundRemark").value = "";
        UI.openModal(el("bulkRoundModal"));
    }

    function selectedStudentIds() {
        return Array.from(state.selected);
    }

    

    function fileText(file) {
        return new Promise(function (resolve, reject) {
            var reader = new FileReader();
            reader.onload = function () { resolve(String(reader.result)); };
            reader.onerror = function () { reject(new Error("Could not read the file")); };
            reader.readAsText(file, "UTF-8");
        });
    }

    function setupImport(file) {
        if (!file.name || !file.name.toLowerCase().endsWith(".csv")) {
            UI.showToast("Choose a .csv file", "error");
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            UI.showToast("CSV files must be 5 MB or smaller", "error");
            return;
        }
        fileText(file).then(function (content) {
            if (!content.trim()) throw new Error("The CSV file is empty");
            state.csvContent = content;
            el("fileNameHint").textContent = file.name + " selected";
            var operations = {
                ADD: { title: "Add New Students", desc: "Insert students that do not yet exist. Enrollment and Course (B.Tech, MCA, or BCA) are required." },
                UPDATE: { title: "Update Existing Students", desc: "Merge academic & contact fields for existing students." },
                STATUS: { title: "Update Student Status", desc: "Set Active / Inactive / Placed / Not Placed." },
                APPLICATION_STATUS: { title: "Update Application Status", desc: "Change an application's status using jobId." },
                APPLICATION_ROUND: { title: "Update Application Round", desc: "Move an application to a target round using jobId." },
                ACADEMIC: { title: "Update Academic Information", desc: "Batch, branch, course, CGPA and percentages." }
            };
            var grid = el("operationGrid");
            grid.innerHTML = Object.keys(operations).map(function (key) {
                var spec = operations[key];
                return '<div class="operation-option" data-op="' + key + '"><strong>' + UI.escapeHtml(spec.title) + "</strong><small>" + UI.escapeHtml(spec.desc) + "</small></div>";
            }).join("");
            grid.querySelectorAll(".operation-option").forEach(function (opt) {
                opt.addEventListener("click", function () {
                    grid.querySelectorAll(".operation-option").forEach(function (o) { o.classList.remove("selected"); });
                    opt.classList.add("selected");
                    state.operation = opt.getAttribute("data-op");
                });
            });
            var first = grid.querySelector(".operation-option");
            if (first) {
                first.classList.add("selected");
                state.operation = first.getAttribute("data-op");
            }
            showImportStep(2);
        }).catch(function (err) {
            UI.showToast(err.message || "Could not read the file", "error");
        });
    }

    function showImportStep(step) {
        for (var i = 1; i <= 5; i++) {
            el("importStep" + i).style.display = i === step ? "" : "none";
        }
        var dots = el("importSteps").querySelectorAll(".step-dot");
        dots.forEach(function (dot, index) {
            var number = index + 1;
            dot.classList.remove("active", "done");
            if (number < step || step === 6) dot.classList.add("done");
            else if (number === step) dot.classList.add("active");
        });
    }

    function validateCsv() {
        el("validateCsvBtn").disabled = true;
        var original = el("validateCsvBtn").textContent;
        el("validateCsvBtn").textContent = "Validating...";

        var form = new FormData();
        form.append("operation", state.operation);
        var blob = new Blob([state.csvContent], { type: "text/csv" });
        form.append("file", blob, "upload.csv");

        fetch("/admin/students/import/validate", { method: "POST", credentials: "include", body: form })
            .then(function (res) {
                return res.text().then(function (text) {
                    var data = null;
                    try { data = text ? JSON.parse(text) : null; } catch (e) {}
                    if (UI.redirectIfUnauthorized(res)) throw new Error("Unauthorized");
                    if (!res.ok) throw new Error(data && data.message ? data.message : "Validation failed");
                    return data;
                });
            })
            .then(function (result) {
                renderValidation(result);
                showImportStep(3);
            })
            .catch(function (err) {
                UI.showToast(err.message || "Could not validate the file", "error");
            })
            .finally(function () {
                el("validateCsvBtn").disabled = false;
                el("validateCsvBtn").textContent = original;
            });
    }

    function renderValidation(result) {
        var summary = el("validationSummary");
        summary.innerHTML = "" +
            '<div class="stat-card"><strong>' + formatNumber(result.totalRows) + "</strong><span>Rows</span></div>" +
            '<div class="stat-card"><strong>' + formatNumber(result.validRows) + "</strong><span>Valid</span></div>" +
            '<div class="stat-card"><strong>' + formatNumber(result.errorRows) + "</strong><span>Errors</span></div>";

        el("validationGlobalErrors").textContent = (result.globalErrors || []).join("; ");

        var tbody = el("validationTableBody");
        var visible = result.rows.slice(0, 500);
        tbody.innerHTML = visible.map(function (row) {
            var cls = row.valid ? "" : "style=\"color:var(--danger)\"";
            return "<tr>" +
                "<td>" + row.rowNumber + "</td>" +
                "<td>" + UI.escapeHtml(row.enrollment || "") + "</td>" +
                "<td>" + UI.escapeHtml(row.name || "") + "</td>" +
                "<td>" + UI.escapeHtml(row.email || "") + "</td>" +
                "<td>" + UI.escapeHtml(row.status || "") + "</td>" +
                "<td " + cls + ">" + (row.valid ? "Valid" : "Invalid — " + UI.escapeHtml(row.error || "check the row")) + "</td>" +
                "</tr>";
        }).join("");
        if (!visible.length) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text-muted)">No rows.</td></tr>';
        }
        if (result.rows.length > 500) {
            el("validationGlobalErrors").textContent += " Showing first 500 row results.";
        }
        state.importData = result;
    }

    function renderReview() {
        var result = state.importData;
        el("reviewStats").innerHTML =
            '<div class="review-stat-card"><strong>' + formatNumber(result.validRows) + "</strong><span>Records to process</span></div>" +
            '<div class="review-stat-card"><strong>' + formatNumber(result.errorRows) + "</strong><span>Skipped (invalid)</span></div>";
        el("confirmImportBtn").textContent = state.operation === "ADD" ? "Import Valid Students" : "Confirm & Update";
        showImportStep(4);
    }

    function confirmImport() {
        el("confirmImportBtn").disabled = true;
        var original = el("confirmImportBtn").textContent;
        el("confirmImportBtn").textContent = "Importing...";

        var lines = state.csvContent.split(/\r?\n/);
        api("/admin/students/import/commit", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ operation: state.operation, rows: lines })
        }).then(function (result) {
            state.importResult = result;
            var successCount = state.operation === "ADD" ? result.createdRows : result.successfulRows;
            var failures = result.errors || [];
            el("importResultBox").innerHTML =
                '<div class="review-stats">' +
                '<div class="review-stat-card"><strong>' + formatNumber(result.totalRows) + "</strong><span>Total Rows</span></div>" +
                '<div class="review-stat-card"><strong>' + formatNumber(successCount) + "</strong><span>Successfully " + (state.operation === "ADD" ? "Added" : "Processed") + "</span></div>" +
                '<div class="review-stat-card"><strong>' + formatNumber(result.failedRows) + "</strong><span>Not Added</span></div>" +
                "</div>";
            if (result.failedRows > 0) {
                el("importFailureTableBody").innerHTML = failures.map(function (failure) {
                    return "<tr>" +
                        "<td>" + formatNumber(failure.rowNumber) + "</td>" +
                        "<td>" + UI.escapeHtml(failure.enrollmentNo || "") + "</td>" +
                        "<td>" + UI.escapeHtml(failure.name || "") + "</td>" +
                        "<td>" + UI.escapeHtml(failure.email || "") + "</td>" +
                        "<td>" + UI.escapeHtml(failure.reason || failure.error || "Could not process row") + "</td>" +
                        "</tr>";
                }).join("");
                el("importFailureSection").style.display = "";
            } else {
                el("importFailureSection").style.display = "none";
                el("importFailureTableBody").innerHTML = "";
            }
            el("downloadErrorReportBtn").style.display = result.failedRows > 0 ? "" : "none";
            showImportStep(5);
            loadStudents();
            loadStats();
        }).catch(function (err) {
            UI.showToast(err.message || "Import failed", "error");
        }).finally(function () {
            el("confirmImportBtn").disabled = false;
            el("confirmImportBtn").textContent = original;
        });
    }

    function downloadErrorReport() {
        if (!state.importResult || state.importResult.failedRows === 0) return;
        downloadCsv("/admin/students/import/error-report",
            { operation: state.operation, errors: state.importResult.errors || [] }, "import_error_report.csv").then(function () {
                UI.showToast("Error report downloaded.", "success");
            }).catch(function (err) {
                UI.showToast(err.message || "Could not generate error report", "error");
            });
    }

    

    function columnOptions() {
        return [
            { key: "Name", label: "Name" },
            { key: "Email", label: "Email" },
            { key: "Phone", label: "Phone" },
            { key: "Enrollment", label: "Enrollment Number" },
            { key: "Branch", label: "Branch" },
            { key: "Section", label: "Section" },
            { key: "Batch", label: "Batch" },
            { key: "CGPA", label: "CGPA" },
            { key: "Student Status", label: "Student Status" },
            { key: "Applications", label: "Applications" },
            { key: "Applied Companies", label: "Applied Companies" },
            { key: "Applied Roles", label: "Applied Roles" },
            { key: "Selected Courses", label: "Selected Courses" },
            { key: "Application Status", label: "Application Status" },
            { key: "Current Round", label: "Current Round" },
            { key: "Course", label: "Course" },
            { key: "College", label: "College" },
            { key: "10th %", label: "10th %" },
            { key: "12th %", label: "12th %" },
            { key: "Graduation %", label: "Graduation %" },
            { key: "DOB", label: "DOB" },
            { key: "Address", label: "Address" },
            { key: "City", label: "City" },
            { key: "State", label: "State" }
        ];
    }

    var DEFAULT_COLUMNS = ["Name", "Email", "Phone", "Enrollment", "Course", "Branch", "Section", "Batch",
        "CGPA", "10th %", "12th %", "Graduation %", "Student Status", "Applications", "Applied Companies",
        "Applied Roles", "Selected Courses", "Application Status", "Current Round"];

    function renderColumns(containerId, selected) {
        var selectedSet = selected || DEFAULT_COLUMNS;
        var container = el(containerId);
        container.innerHTML = columnOptions().map(function (col) {
            var checked = selectedSet.indexOf(col.key) >= 0 ? "checked" : "";
            return '<label><input type="checkbox" value="' + UI.escapeHtml(col.key) + '"' + checked + "> " + UI.escapeHtml(col.label) + "</label>";
        }).join("");
    }

    function selectedColumns(containerId) {
        return Array.from(el(containerId).querySelectorAll("input:checked")).map(function (box) {
            return box.value;
        });
    }

    function exportScopeValue() {
        var selected = Array.from(state.selected);
        var scope = el("exportScopeSelect").value;
        if (scope === "selected" && !selected.length) {
            UI.showToast("No students are selected. Option will fall back to current filtered students.", "warning");
            scope = "filtered";
        }
        return { scope: scope, ids: selected, filters: {
            search: state.search, batch: state.filters.batch ? [state.filters.batch] : [],
            branch: state.filters.branch ? [state.filters.branch] : [],
            section: state.filters.section ? [state.filters.section] : [],
            studentStatus: state.filters.studentStatus, applicationStatus: state.filters.applicationStatus,
            company: state.filters.company, job: state.filters.job, round: state.filters.round,
            appliedFrom: state.filters.appliedFrom, appliedTo: state.filters.appliedTo
        } };
    }

    function generateExport() {
        var scopeData = exportScopeValue();
        el("generateExportBtn").disabled = true;
        var original = el("generateExportBtn").textContent;
        el("generateExportBtn").textContent = "Generating...";

        downloadCsv("/admin/students/export", {
            scope: scopeData.scope, studentIds: scopeData.ids,
            search: scopeData.filters.search || null,
            batch: scopeData.filters.batch.length ? scopeData.filters.batch : null,
            branch: scopeData.filters.branch.length ? scopeData.filters.branch : null,
            section: scopeData.filters.section.length ? scopeData.filters.section : null,
            studentStatus: scopeData.filters.studentStatus || null,
            applicationStatus: scopeData.filters.applicationStatus || null,
            company: scopeData.filters.company || null,
            job: scopeData.filters.job || null,
            round: scopeData.filters.round ? parseInt(scopeData.filters.round, 10) : null,
            appliedFrom: scopeData.filters.appliedFrom || null,
            appliedTo: scopeData.filters.appliedTo || null,
            columns: selectedColumns("exportColumns")
        }, "students_export.csv").then(function () {
            UI.showToast("CSV downloaded.", "success");
        }).catch(function (err) {
            UI.showToast(err.message || "Could not generate CSV", "error");
        }).finally(function () {
            el("generateExportBtn").disabled = false;
            el("generateExportBtn").textContent = original;
        });
    }

    function sendEmail() {
        var recipient = el("emailRecipient").value.trim();
        if (!recipient) {
            UI.showToast("Enter a recipient email address", "error");
            return;
        }
        var selected = Array.from(state.selected);
        var scope = el("emailScopeSelect").value;
        if (scope === "selected" && !selected.length) {
            UI.showToast("No students are selected. The email will include current filtered students.", "warning");
            scope = "filtered";
        }
        el("sendEmailBtn").disabled = true;
        var original = el("sendEmailBtn").textContent;
        el("sendEmailBtn").textContent = "Sending...";

        api("/admin/students/email-csv", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                recipientEmail: recipient,
                scope: scope,
                studentIds: selected,
                search: state.search.trim() || null,
                batch: state.filters.batch ? [state.filters.batch] : null,
                branch: state.filters.branch ? [state.filters.branch] : null,
                section: state.filters.section ? [state.filters.section] : null,
                studentStatus: state.filters.studentStatus || null,
                applicationStatus: state.filters.applicationStatus || null,
                company: state.filters.company || null,
                job: state.filters.job || null,
                round: state.filters.round ? parseInt(state.filters.round, 10) : null,
                appliedFrom: state.filters.appliedFrom || null,
                appliedTo: state.filters.appliedTo || null,
                columns: selectedColumns("emailColumns")
            })
        }).then(function (data) {
            UI.showToast("Emailed CSV to " + data.recipient + " (" + data.students + " students)", "success");
            UI.closeModal(el("emailModal"));
        }).catch(function (err) {
            UI.showToast(err.message || "Could not send the email", "error");
        }).finally(function () {
            el("sendEmailBtn").disabled = false;
            el("sendEmailBtn").textContent = original;
        });
    }

    

    function setStudentFieldError(fieldId, message) {
        var errorEl = document.querySelector('[data-error-for="' + fieldId + '"]');
        if (!errorEl) return;
        errorEl.textContent = message || "";
        if (fieldId) {
            var input = document.getElementById(fieldId);
            if (input) input.style.borderColor = message ? "var(--danger)" : "";
        }
    }

    function clearStudentFieldErrors() {
        ["sEnrollment", "sName", "sEmail", "sPassword", "sPhone", "sBatch", "sBranch", "sCourse", "sSection", "sStatus", "sTenth", "sTwelfth"].forEach(function (fieldId) {
            setStudentFieldError(fieldId, "");
        });
    }

    function updateBranchField() {
        var isBTech = el("sCourse").value === "B.Tech";
        el("branchFieldGroup").style.display = isBTech ? "" : "none";
        el("sBranch").required = isBTech;
        if (!isBTech) {
            el("sBranch").value = "";
            setStudentFieldError("sBranch", "");
        }
    }

    function saveStudent() {
        clearStudentFieldErrors();
        var payload = {
            enrollmentno: el("sEnrollment").value.trim(),
            name: el("sName").value.trim(),
            email: el("sEmail").value.trim(),
            password: el("sPassword").value,
            phoneno: el("sPhone").value.trim(),
            batch: el("sBatch").value.trim(),
            branch: el("sBranch").value.trim(),
            course: el("sCourse").value,
            section: el("sSection").value.trim(),
            studentStatus: el("sStatus").value,
            tenthPercentage: el("sTenth").value.trim() === "" ? null : parseFloat(el("sTenth").value),
            twelfthPercentage: el("sTwelfth").value.trim() === "" ? null : parseFloat(el("sTwelfth").value)
        };

        var errors = [];
        if (!payload.enrollmentno) errors.push(["sEnrollment", "Enrollment number is required"]);
        if (!payload.name) errors.push(["sName", "Name is required"]);
        if (!payload.email) errors.push(["sEmail", "Email is required"]);
        else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) errors.push(["sEmail", "Email must be in a valid format"]);
        if (!payload.password) errors.push(["sPassword", "Password is required"]);
        if (!payload.phoneno) errors.push(["sPhone", "Phone number is required"]);
        if (!payload.batch) errors.push(["sBatch", "Batch is required"]);
        if (payload.course === "B.Tech" && !payload.branch) errors.push(["sBranch", "Branch is required"]);
        if (!payload.course) errors.push(["sCourse", "Course is required"]);
        if (!payload.section) errors.push(["sSection", "Section is required"]);
        if (!payload.studentStatus) errors.push(["sStatus", "Student status is required"]);
        if (payload.tenthPercentage === null || payload.tenthPercentage === undefined || Number.isNaN(payload.tenthPercentage)) {
            errors.push(["sTenth", "10th Percentage is required"]);
        } else if (payload.tenthPercentage < 0 || payload.tenthPercentage > 100) {
            errors.push(["sTenth", "10th Percentage must be between 0 and 100"]);
        }
        if (payload.twelfthPercentage === null || payload.twelfthPercentage === undefined || Number.isNaN(payload.twelfthPercentage)) {
            errors.push(["sTwelfth", "12th Percentage is required"]);
        } else if (payload.twelfthPercentage < 0 || payload.twelfthPercentage > 100) {
            errors.push(["sTwelfth", "12th Percentage must be between 0 and 100"]);
        }

        if (errors.length) {
            errors.forEach(function (entry) {
                setStudentFieldError(entry[0], entry[1]);
            });
            UI.showToast(errors[0][1], "error");
            return;
        }

        el("saveStudentBtn").disabled = true;
        var original = el("saveStudentBtn").textContent;
        el("saveStudentBtn").textContent = "Saving...";
        api("/admin/students", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        }).then(function (data) {
            UI.showToast("Student " + (data.name || data.enrollmentno) + " created", "success");
            UI.closeModal(el("studentModal"));
            loadStats();
            loadStudents();
            loadFilterOptions();
        }).catch(function (err) {
            UI.showToast(err.message || "Could not create student", "error");
        }).finally(function () {
            el("saveStudentBtn").disabled = false;
            el("saveStudentBtn").textContent = original;
        });
    }

    

    function init() {
        loadStats();
        loadFilterOptions();
        loadStudents();

        var searchBox = el("studentQuery");
        searchBox.addEventListener("input", function () {
            var value = searchBox.value;
            el("searchClearBtn").classList.toggle("hidden", !value);
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(function () {
                state.search = value;
                state.page = 1;
                loadStudents();
                updateFilterCount();
            }, 400);
        });
        el("searchClearBtn").addEventListener("click", function () {
            searchBox.value = "";
            el("searchClearBtn").classList.add("hidden");
            state.search = "";
            state.page = 1;
            loadStudents();
            updateFilterCount();
        });

        el("filterToggleBtn").addEventListener("click", function () {
            var panel = el("filterPanel");
            var open = panel.style.display === "none";
            panel.style.display = open ? "" : "none";
            el("filterToggleBtn").setAttribute("aria-expanded", open ? "true" : "false");
        });

        el("applyFiltersBtn").addEventListener("click", function () {
            state.filters = {
                batch: el("batchFilter").value,
                branch: el("branchFilter").value,
                section: el("sectionFilter").value,
                studentStatus: el("studentStatusFilter").value,
                company: el("companyFilter").value,
                job: el("jobFilter").value,
                applicationStatus: el("applicationStatusFilter").value,
                round: el("roundFilter").value,
                appliedFrom: el("appliedFromFilter").value,
                appliedTo: el("appliedToFilter").value
            };
            state.page = 1;
            loadStudents();
            updateFilterCount();
        });

        el("resetFiltersBtn").addEventListener("click", function () {
            state.filters = { batch: "", branch: "", section: "", studentStatus: "", company: "", job: "",
                applicationStatus: "", round: "", appliedFrom: "", appliedTo: "" };
            syncFilterControls();
            state.page = 1;
            loadStudents();
        });

        el("emptyClearBtn").addEventListener("click", function () {
            el("studentQuery").value = "";
            el("searchClearBtn").classList.add("hidden");
            state.search = "";
            el("resetFiltersBtn").click();
        });

        el("selectAllCheckbox").addEventListener("change", function () {
            var checked = el("selectAllCheckbox").checked;
            state.students.forEach(function (student) {
                if (checked) state.selected.add(student.id);
                else state.selected.delete(student.id);
            });
            renderStudents();
            updateSelectionUI();
        });

        el("bulkClearBtn").addEventListener("click", function () {
            state.selected.clear();
            updateSelectionUI();
            renderStudents();
        });

        el("bulkChangeStatusBtn").addEventListener("click", bulkStatus);
        el("cancelBulkStatusBtn").addEventListener("click", function () { UI.closeModal(el("bulkStatusModal")); });
        el("applyBulkStatusBtn").addEventListener("click", function () {
            var status = el("bulkStatusSelect").value;
            var ids = selectedStudentIds();
            if (!ids.length) return;
            el("applyBulkStatusBtn").disabled = true;
            var original = el("applyBulkStatusBtn").textContent;
            el("applyBulkStatusBtn").textContent = "Updating...";
            api("/admin/students/bulk-status", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ studentIds: ids, status: status })
            }).then(function (result) {
                UI.showToast(result.message || "Students updated", "success");
                UI.closeModal(el("bulkStatusModal"));
                state.selected.clear();
                loadStudents();
                loadStats();
            }).catch(function (err) {
                UI.showToast(err.message || "Bulk update failed", "error");
            }).finally(function () {
                el("applyBulkStatusBtn").disabled = false;
                el("applyBulkStatusBtn").textContent = original;
            });
        });

        el("bulkChangeRoundBtn").addEventListener("click", bulkRound);
        el("cancelBulkRoundBtn").addEventListener("click", function () { UI.closeModal(el("bulkRoundModal")); });
        el("applyBulkRoundBtn").addEventListener("click", function () {
            var targetRound = parseInt(el("bulkRoundInput").value, 10);
            if (!targetRound || targetRound < 1) {
                UI.showToast("Target round must be at least 1", "error");
                return;
            }
            var resultEl = el("applyBulkRoundBtn");
            resultEl.disabled = true;
            var original = resultEl.textContent;
            resultEl.textContent = "Moving...";
            api("/admin/students/bulk-round", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ studentIds: selectedStudentIds(), targetRound: targetRound, adminRemark: el("bulkRoundRemark").value.trim() })
            }).then(function (result) {
                UI.showToast(result.message || "Applications moved", "success");
                UI.closeModal(el("bulkRoundModal"));
                state.selected.clear();
                loadStudents();
                loadStats();
            }).catch(function (err) {
                UI.showToast(err.message || "Bulk move failed", "error");
            }).finally(function () {
                resultEl.disabled = false;
                resultEl.textContent = original;
            });
        });
        el("cancelStudentBtn").addEventListener("click", function () { UI.closeModal(el("studentModal")); });
        el("saveStudentBtn").addEventListener("click", saveStudent);
        el("sCourse").addEventListener("change", updateBranchField);
        el("addStudentBtn").addEventListener("click", function () {
            ["sEnrollment", "sName", "sEmail", "sPassword", "sPhone", "sBatch", "sBranch", "sCourse", "sSection", "sTenth", "sTwelfth"].forEach(function (id) {
                var input = document.getElementById(id);
                if (input) input.value = "";
            });
            clearStudentFieldErrors();
            updateBranchField();
            el("sStatus").value = "Active";
            UI.openModal(el("studentModal"));
        });

        
        el("importCsvBtn").addEventListener("click", function () {
            state.csvContent = null;
            state.operation = "ADD";
            state.importResult = null;
            el("fileNameHint").textContent = "";
            el("csvFileInput").value = "";
            el("importResultBox").innerHTML = "";
            showImportStep(1);
            UI.openModal(el("importModal"));
        });

        var dropZone = el("dropZone");
        ["dragenter", "dragover"].forEach(function (event) {
            dropZone.addEventListener(event, function (e) {
                e.preventDefault();
                dropZone.classList.add("dragover");
            });
        });
        ["dragleave", "drop"].forEach(function (event) {
            dropZone.addEventListener(event, function (e) {
                e.preventDefault();
                dropZone.classList.remove("dragover");
            });
        });
        dropZone.addEventListener("drop", function (e) {
            var file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
            if (file) setupImport(file);
        });
        el("chooseFileBtn").addEventListener("click", function (e) {
            e.stopPropagation();
            el("csvFileInput").click();
        });
        el("csvFileInput").addEventListener("change", function () {
            var file = el("csvFileInput").files[0];
            if (file) setupImport(file);
        });

        el("downloadTemplateBtn").addEventListener("click", function () {
            var operation = "ADD";
            var url = "/admin/students/template?operation=" + encodeURIComponent(operation);
            window.open(url, "_blank");
            el("fileNameHint").textContent = "Template downloaded — choose the operation later, or add your data now.";
        });

        el("backToUploadBtn").addEventListener("click", function () { showImportStep(1); });
        el("validateCsvBtn").addEventListener("click", validateCsv);
        el("backToConfigureBtn").addEventListener("click", function () { showImportStep(2); });
        el("reviewChangesBtn").addEventListener("click", renderReview);
        el("backToValidateBtn").addEventListener("click", function () { showImportStep(3); });
        el("confirmImportBtn").addEventListener("click", confirmImport);
        el("downloadErrorReportBtn").addEventListener("click", downloadErrorReport);
        el("doneImportBtn").addEventListener("click", function () { UI.closeModal(el("importModal")); });
        el("importModal").querySelector("[data-import-close]").addEventListener("click", function () {
            UI.closeModal(el("importModal"));
        });

        
        el("exportCsvBtn").addEventListener("click", function () {
            renderColumns("exportColumns");
            UI.openModal(el("exportModal"));
        });
        el("selectAllColumnsBtn").addEventListener("click", function () {
            el("exportColumns").querySelectorAll("input").forEach(function (box) { box.checked = true; });
        });
        el("clearAllColumnsBtn").addEventListener("click", function () {
            el("exportColumns").querySelectorAll("input").forEach(function (box) { box.checked = false; });
        });
        el("cancelExportBtn").addEventListener("click", function () { UI.closeModal(el("exportModal")); });
        el("generateExportBtn").addEventListener("click", generateExport);
        el("bulkExportBtn").addEventListener("click", function () {
            el("exportScopeSelect").value = "selected";
            renderColumns("exportColumns");
            UI.openModal(el("exportModal"));
        });

        
        el("emailCsvBtn").addEventListener("click", function () {
            el("emailRecipient").value = "";
            el("emailScopeSelect").value = state.selected.size ? "selected" : "filtered";
            renderColumns("emailColumns");
            UI.openModal(el("emailModal"));
        });
        el("emailSelectAllBtn").addEventListener("click", function () {
            el("emailColumns").querySelectorAll("input").forEach(function (box) { box.checked = true; });
        });
        el("emailClearAllBtn").addEventListener("click", function () {
            el("emailColumns").querySelectorAll("input").forEach(function (box) { box.checked = false; });
        });
        el("cancelEmailBtn").addEventListener("click", function () { UI.closeModal(el("emailModal")); });
        el("sendEmailBtn").addEventListener("click", sendEmail);
        el("bulkEmailBtn").addEventListener("click", function () {
            el("emailRecipient").value = "";
            el("emailScopeSelect").value = "selected";
            renderColumns("emailColumns");
            UI.openModal(el("emailModal"));
        });

        el("pageSizeSelect").addEventListener("change", function () {
            state.pageSize = parseInt(el("pageSizeSelect").value, 10);
            state.page = 1;
            loadStudents();
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();