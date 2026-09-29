/**
 * Admin notice management.
 *
 * Every operation is fetch + JSON; the table row is patched in place and no
 * location.reload() or form submit is used anywhere.
 */
(function () {
    "use strict";

    var API = "/api/admin/notices";

    var body = document.getElementById("noticesBody");
    var emptyMsg = document.getElementById("emptyMsg");
    var searchBox = document.getElementById("searchBox");
    var addBtn = document.getElementById("addNoticeBtn");
    var modal = document.getElementById("noticeModal");
    var modalTitle = document.getElementById("modalTitle");
    var saveBtn = document.getElementById("saveNoticeBtn");

    var fields = {
        title: document.getElementById("fTitle"),
        message: document.getElementById("fMessage"),
        category: document.getElementById("fCategory"),
        link: document.getElementById("fLink"),
        active: document.getElementById("fActive"),
        expiresAt: document.getElementById("fExpiresAt")
    };

    var notices = [];
    var editingId = null;

    function esc(value) {
        return window.UI.escapeHtml(value);
    }

    function messageFrom(response, fallback) {
        return response.json()
            .then(function (body) { return (body && body.message) || fallback; })
            .catch(function () { return fallback; });
    }

    async function load() {
        try {
            var response = await fetch(API, { credentials: "include" });
            if (UI.redirectIfUnauthorized(response)) return;
            if (!response.ok) {
                UI.showToast(await messageFrom(response, "Could not load notices"), "error");
                return;
            }
            notices = await response.json();
            render();
        } catch (e) {
            UI.showToast("Could not load notices", "error");
        }
    }

    function isExpired(notice) {
        if (!notice.expiresAt) return false;
        var stamp = UI.parseTimestamp(notice.expiresAt);
        return stamp ? Date.now() > stamp : false;
    }

    function rowStatus(notice) {
        if (!notice.active) return '<span class="notice-status-pill off">Off</span>';
        if (isExpired(notice)) return '<span class="notice-status-pill off">Expired</span>';
        return '<span class="notice-status-pill on">Active</span>';
    }

    function render() {
        var needle = searchBox.value.toLowerCase().trim();
        var rows = notices.filter(function (notice) {
            if (!needle) return true;
            return [notice.title, notice.message, notice.category]
                .filter(Boolean)
                .some(function (field) { return String(field).toLowerCase().indexOf(needle) >= 0; });
        });

        if (rows.length === 0) {
            body.innerHTML = "";
            emptyMsg.style.display = notices.length === 0 ? "" : "none";
            if (notices.length > 0) {
                body.innerHTML = '<tr><td colspan="6" style="text-align:center; padding:28px; color:var(--text-muted);">'
                    + "No notices match your search.</td></tr>";
            }
            return;
        }

        emptyMsg.style.display = "none";
        body.innerHTML = rows.map(function (notice) {
            var id = esc(notice.id);
            var toggleLabel = notice.active ? "Disable" : "Enable";
            return "<tr>"
                + "<td><strong>" + esc(notice.title) + "</strong><br>"
                + '<span style="font-size:12px; color:var(--text-muted);">'
                + esc((notice.message || "").slice(0, 90)) + "</span></td>"
                + "<td>" + (notice.category ? esc(notice.category) : "<span style='color:var(--text-subtle)'>—</span>") + "</td>"
                + "<td>" + rowStatus(notice) + "</td>"
                + "<td class='notice-expiry'>" + (notice.expiresAt ? esc(UI.fmtDateTime(notice.expiresAt)) : "—") + "</td>"
                + "<td>" + esc(UI.fmtDate(notice.createdAt)) + "</td>"
                + '<td><div class="notice-row-actions">'
                + '<button class="btn btn-small btn-secondary" data-act="edit" data-id="' + id + '">Edit</button>'
                + '<button class="btn btn-small btn-secondary" data-act="toggle" data-id="' + id + '">' + toggleLabel + "</button>"
                + '<button class="btn btn-small btn-danger" data-act="delete" data-id="' + id + '">Delete</button>'
                + "</div></td></tr>";
        }).join("");
    }

    function openModal(notice) {
        editingId = notice ? notice.id : null;
        modalTitle.textContent = notice ? "Edit Notice" : "Add Notice";
        fields.title.value = notice ? (notice.title || "") : "";
        fields.message.value = notice ? (notice.message || "") : "";
        fields.category.value = notice ? (notice.category || "") : "";
        fields.link.value = notice ? (notice.link || "") : "";
        fields.active.value = notice ? String(notice.active !== false) : "true";
        // datetime-local needs a local "YYYY-MM-DDTHH:mm" string, not an ISO instant.
        fields.expiresAt.value = notice && notice.expiresAt
            ? toLocalInput(notice.expiresAt)
            : "";
        UI.openModal(modal);
    }

    function toLocalInput(value) {
        var parsed = UI.parseDate(value);
        if (!parsed) return "";
        function pad(n) { return String(n).padStart(2, "0"); }
        return parsed.getFullYear() + "-" + pad(parsed.getMonth() + 1) + "-" + pad(parsed.getDate())
            + "T" + pad(parsed.getHours()) + ":" + pad(parsed.getMinutes());
    }

    function closeModal() {
        UI.closeModal(modal);
    }

    function payload() {
        var body = {
            title: fields.title.value.trim(),
            message: fields.message.value.trim(),
            category: fields.category.value.trim() || null,
            link: fields.link.value.trim() || null,
            active: fields.active.value === "true",
            expiresAt: fields.expiresAt.value ? fields.expiresAt.value + ":00" : null
        };
        return body;
    }

    async function save() {
        var data = payload();
        if (!data.title) {
            UI.showToast("Title is required", "error");
            return;
        }
        if (!data.message) {
            UI.showToast("Message is required", "error");
            return;
        }
        UI.setButtonLoading(saveBtn, true, "Saving...");
        try {
            var response = await fetch(editingId ? API + "/" + editingId : API, {
                method: editingId ? "PUT" : "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data)
            });
            if (!response.ok) {
                UI.showToast(await messageFrom(response, "Could not save the notice"), "error");
                return;
            }
            // Patch the in-memory list and re-render the table; the page never reloads.
            var saved = await response.json();
            if (editingId) {
                notices = notices.map(function (item) { return item.id === saved.id ? saved : item; });
            } else {
                notices.unshift(saved);
            }
            notices.sort(function (a, b) {
                return UI.parseTimestamp(b.updatedAt || b.createdAt)
                    - UI.parseTimestamp(a.updatedAt || a.createdAt);
            });
            render();
            closeModal();
            UI.showToast(editingId ? "Notice updated" : "Notice created", "success");
        } catch (e) {
            UI.showToast("Could not save the notice", "error");
        } finally {
            UI.setButtonLoading(saveBtn, false);
        }
    }

    async function toggle(id) {
        var notice = notices.find(function (item) { return item.id === id; });
        if (!notice) return;
        var next = !notice.active;
        try {
            var response = await fetch(API + "/" + id + "/status", {
                method: "PATCH",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ active: next })
            });
            if (!response.ok) {
                UI.showToast(await messageFrom(response, "Could not update the notice"), "error");
                return;
            }
            var updated = await response.json();
            notices = notices.map(function (item) { return item.id === id ? updated : item; });
            render();
            UI.showToast(updated.active ? "Notice enabled" : "Notice disabled", "success");
        } catch (e) {
            UI.showToast("Could not update the notice", "error");
        }
    }

    async function remove(id) {
        var confirmed = await UI.confirmAction("This notice will be permanently deleted.", {
            title: "Delete notice?",
            confirmText: "Delete",
            cancelText: "Cancel",
            danger: true
        });
        if (!confirmed) return;
        try {
            var response = await fetch(API + "/" + id, {
                method: "DELETE",
                credentials: "include"
            });
            if (!response.ok) {
                UI.showToast(await messageFrom(response, "Could not delete the notice"), "error");
                return;
            }
            notices = notices.filter(function (item) { return item.id !== id; });
            render();
            UI.showToast("Notice deleted", "success");
        } catch (e) {
            UI.showToast("Could not delete the notice", "error");
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        addBtn.addEventListener("click", function () { openModal(null); });
        saveBtn.addEventListener("click", save);
        document.getElementById("cancelNoticeBtn").addEventListener("click", closeModal);
        document.getElementById("closeNoticeBtn").addEventListener("click", closeModal);
        searchBox.addEventListener("input", render);

        modal.addEventListener("click", function (event) {
            if (event.target === modal) closeModal();
        });

        body.addEventListener("click", function (event) {
            var button = event.target.closest("[data-act]");
            if (!button) return;
            var id = button.getAttribute("data-id");
            var action = button.getAttribute("data-act");
            if (action === "edit") {
                openModal(notices.find(function (item) { return item.id === id; }));
            } else if (action === "toggle") {
                toggle(id);
            } else if (action === "delete") {
                remove(id);
            }
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && modal.classList.contains("open")) closeModal();
        });

        load();
    });
})();
