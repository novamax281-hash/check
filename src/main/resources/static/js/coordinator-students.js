(function () {
    "use strict";

    var UI = window.UI;

    var state = {
        page: 1,
        pageSize: 25,
        total: 0,
        totalPages: 1,
        search: "",
        branch: "",
        section: "",
        batch: "",
        studentStatus: "",
        applicationStatus: "",
        company: "",
        job: "",
        round: "",
        students: []
    };

    var debounceTimer = null;

    var params = new URLSearchParams(window.location.search);
    if (params.get("branch")) state.branch = params.get("branch");
    if (params.get("section")) state.section = params.get("section");
    if (params.get("batch")) state.batch = params.get("batch");

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

    function populateSelect(select, values) {
        var current = select.value;
        var options = ['<option value="">All</option>'].concat(
            (values || []).map(function (v) {
                return '<option value="' + UI.escapeHtml(v) + '">' + UI.escapeHtml(v) + "</option>";
            })
        ).join("");
        select.innerHTML = options;
        select.value = current;
    }

    function buildQuery() {
        var q = new URLSearchParams();
        q.set("page", state.page);
        q.set("pageSize", state.pageSize);
        if (state.search) q.set("search", state.search);
        if (state.branch) q.set("branch", state.branch);
        if (state.section) q.set("section", state.section);
        if (state.batch) q.set("batch", state.batch);
        if (state.studentStatus) q.set("studentStatus", state.studentStatus);
        if (state.applicationStatus) q.set("applicationStatus", state.applicationStatus);
        if (state.company) q.set("company", state.company);
        if (state.job) q.set("job", state.job);
        if (state.round) q.set("round", state.round);
        return "/coordinator/students?" + q.toString();
    }

    function loadFilterOptions() {
        api("/coordinator/students?page=1&pageSize=1").then(function (data) {
            var options = data.filterOptions || {};
            populateSelect(el("branchFilter"), options.branches || []);
            populateSelect(el("sectionFilter"), options.sections || []);
            populateSelect(el("batchFilter"), options.batches || []);
            populateSelect(el("studentStatusFilter"), options.studentStatuses || []);
            populateSelect(el("applicationStatusFilter"), options.applicationStatuses || []);
            populateSelect(el("companyFilter"), options.companies || []);
            populateSelect(el("jobFilter"), options.jobs || []);
            populateSelect(el("roundFilter"), options.rounds || []);
            el("branchFilter").value = state.branch;
            el("sectionFilter").value = state.section;
            el("batchFilter").value = state.batch;
            el("studentStatusFilter").value = state.studentStatus;
            el("applicationStatusFilter").value = state.applicationStatus;
            el("companyFilter").value = state.company;
            el("jobFilter").value = state.job;
            el("roundFilter").value = state.round;
        }).catch(function () {});
    }

    function loadStudents() {
        var tbody = el("studentsBody");
        var emptyMsg = el("emptyMsg");
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:24px;color:var(--text-muted)">Loading students...</td></tr>';

        api(buildQuery()).then(function (data) {
            state.students = data.students || [];
            state.total = data.total || 0;
            state.totalPages = data.totalPages || 1;
            renderStudents();
            renderPagination();
            el("resultCount").textContent = state.total + " student" + (state.total === 1 ? "" : "s") + " found";
            emptyMsg.classList.add("hidden");
        }).catch(function (err) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:24px;color:var(--text-muted)">' +
                UI.escapeHtml(err.message || "Could not load students") + "</td></tr>";
        });
    }

    function renderStudents() {
        var tbody = el("studentsBody");
        var emptyMsg = el("emptyMsg");
        if (!state.students.length) {
            tbody.innerHTML = "";
            emptyMsg.classList.remove("hidden");
            el("emptyTitle").textContent = state.search ? "No student found for '" + state.search + "'." : "No students found.";
            el("emptySub").textContent = state.search ? "" : "No students match the current filters.";
            el("resultCount").textContent = "0 students found";
            return;
        }
        tbody.innerHTML = state.students.map(function (student) {
            var statusLabel = student.studentStatus || "Active";
            var badgeClass = UI.badge(statusLabel);
            return "<tr>" +
                '<td><div class="student-cell">' +
                '<span class="student-avatar">' + UI.escapeHtml(UI.initials(student.name, student.enrollmentno)) + "</span>" +
                '<div><div class="student-name">' + UI.escapeHtml(student.name || "—") + "</div>" +
                '<div class="student-sub">' + UI.escapeHtml(student.enrollmentno || "") + "</div></div></div></td>" +
                "<td>" + UI.escapeHtml(student.email || "—") + "</td>" +
                "<td>" + UI.escapeHtml(student.branch || "—") + "</td>" +
                "<td>" + UI.escapeHtml(student.section || "—") + "</td>" +
                "<td>" + UI.escapeHtml(student.batch || "—") + "</td>" +
                "<td>" + (student.applicationCount || 0) + "</td>" +
                '<td><span class="badge ' + badgeClass + '">' + UI.escapeHtml(statusLabel) + "</span></td>" +
                '<td class="table-actions"><a class="btn btn-small btn-secondary" href="coordinator-student-detail.html?id=' +
                encodeURIComponent(student.id) + '">View</a></td>' +
                "</tr>";
        }).join("");
    }

    function renderPagination() {
        var container = el("pagination");
        if (state.totalPages <= 1) {
            container.innerHTML = "";
            return;
        }
        var buttons = "";
        for (var i = 1; i <= state.totalPages; i++) {
            if (state.totalPages > 7 && i > 2 && i < state.totalPages - 1 && Math.abs(i - state.page) > 1) {
                if (buttons.charAt(buttons.length - 1) !== "…") buttons += '<span class="page-gap">…</span>';
                continue;
            }
            var active = i === state.page ? " active" : "";
            buttons += '<button class="page-btn' + active + '" data-page="' + i + '">' + i + "</button>";
        }
        container.innerHTML = buttons;
        container.querySelectorAll(".page-btn").forEach(function (btn) {
            btn.addEventListener("click", function () {
                state.page = parseInt(btn.getAttribute("data-page"), 10);
                loadStudents();
            });
        });
    }

    function init() {
        var searchBox = el("searchBox");
        searchBox.value = state.search;
        searchBox.addEventListener("input", function () {
            el("searchClearBtn").classList.toggle("hidden", !searchBox.value);
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(function () {
                state.search = searchBox.value;
                state.page = 1;
                loadStudents();
            }, 400);
        });
        el("searchClearBtn").addEventListener("click", function () {
            searchBox.value = "";
            el("searchClearBtn").classList.add("hidden");
            state.search = "";
            state.page = 1;
            loadStudents();
        });
        el("branchFilter").addEventListener("change", function () { state.branch = this.value; state.page = 1; loadStudents(); });
        el("sectionFilter").addEventListener("change", function () { state.section = this.value; state.page = 1; loadStudents(); });
        el("batchFilter").addEventListener("change", function () { state.batch = this.value; state.page = 1; loadStudents(); });
        ["studentStatus", "applicationStatus", "company", "job", "round"].forEach(function (key) {
            el(key + "Filter").addEventListener("change", function () { state[key] = this.value; state.page = 1; loadStudents(); });
        });
        el("resetFiltersBtn").addEventListener("click", function () {
            state.branch = state.section = state.batch = state.studentStatus = state.applicationStatus = "";
            state.company = state.job = state.round = "";
            state.page = 1;
            loadFilterOptions();
            loadStudents();
        });

        loadFilterOptions();
        loadStudents();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();