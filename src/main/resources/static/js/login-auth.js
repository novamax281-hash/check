"use strict";

const loginForm = document.getElementById("loginForm");
const loginButton = document.getElementById("loginButton");
const otpSection = document.getElementById("otpSection");
const otpForm = document.getElementById("otpForm");
const otpButton = document.getElementById("otpButton");
const otpInput = document.getElementById("otp");
const otpSentMessage = document.getElementById("otpSentMessage");
const resendLink = document.getElementById("resendOtp");
const backLink = document.getElementById("backToLogin");
const resetSection = document.getElementById("resetSection");
const resetForm = document.getElementById("resetForm");
const resetButton = document.getElementById("resetButton");
const resetEmail = document.getElementById("resetEmail");
const resetOtp = document.getElementById("resetOtp");
const newPassword = document.getElementById("newPassword");
const confirmNewPassword = document.getElementById("confirmNewPassword");
const resendResetLink = document.getElementById("resendResetOtp");
const message = document.getElementById("message");
const resetMessage = document.getElementById("resetMessage");
const toggleAuthMode = document.getElementById("toggleAuthMode");
const authToggleWrap = document.getElementById("authToggleWrap");
const authModeLinks = document.getElementById("authModeLinks");
const forgotPasswordLink = document.getElementById("forgotPasswordLink");
const staffLoginLink = document.getElementById("staffLoginLink");
const authHeading = document.getElementById("authHeading");
const authSubtitle = document.getElementById("authSubtitle");
const studentEmail = document.getElementById("studentEmail");
const studentPassword = document.getElementById("studentPassword");
const regName = document.getElementById("regName");
const regEmail = document.getElementById("regEmail");
const regEnrollment = document.getElementById("regEnrollment");
const regMobile = document.getElementById("regMobile");
const regBatchStart = document.getElementById("regBatchStart");
const regBatchEnd = document.getElementById("regBatchEnd");
const regTenth = document.getElementById("regTenth");
const regTwelfth = document.getElementById("regTwelfth");
const regPassword = document.getElementById("regPassword");
const regPasswordConfirm = document.getElementById("regPasswordConfirm");

const AUTH_FIELDS = [
    "studentEmailGroup", "studentPasswordGroup", "staffEnrollmentGroup", "staffMobileGroup",
    "regNameGroup", "regEmailGroup", "regBatchGroup", "regTenthGroup", "regTwelfthGroup",
    "regEnrollmentGroup", "regMobileGroup", "regPasswordGroup", "regPasswordConfirmGroup"
];

let authMode = "student";
let pendingOtpMode = "staff";
let pendingOtpData = null;
let resetVerified = false;
let cooldownInterval = null;
let resetCooldownInterval = null;
let dailyLimitInterval = null;
let otpDailyLimitReached = false;

function setLinkDisabled(link, disabled) {
    if (disabled) {
        link.setAttribute("aria-disabled", "true");
        link.classList.add("is-disabled");
    } else {
        link.removeAttribute("aria-disabled");
        link.classList.remove("is-disabled");
    }
}

function otpMessageWithAttempts(messageText, response) {
    const remaining = response && response.otpAttemptsRemaining;
    if (!Number.isInteger(remaining)) return messageText;
    let message = messageText + " OTP attempts remaining today: " + remaining + "/5.";
    if (remaining === 0 && !messageText.includes("Daily OTP limit reached.")) {
        message += " Daily OTP limit reached. Please try again tomorrow.";
    }
    return message;
}

function applyDailyLimit(response) {
    if (response && (response.otpAttemptsRemaining === 0
            || String(response.message || "").includes("Daily OTP limit reached."))) {
        otpDailyLimitReached = true;
        loginButton.disabled = true;
        setLinkDisabled(resendLink, true);
        setLinkDisabled(resendResetLink, true);
        startDailyLimitCountdown(response.retryAfterSeconds);
        return true;
    }
    return false;
}

function startDailyLimitCountdown(duration) {
    if (dailyLimitInterval) window.clearInterval(dailyLimitInterval);
    const seconds = Number(duration);
    if (!Number.isFinite(seconds) || seconds <= 0) return;
    let remaining = Math.ceil(seconds);
    dailyLimitInterval = window.setInterval(function() {
        remaining -= 1;
        if (remaining > 0) return;
        window.clearInterval(dailyLimitInterval);
        dailyLimitInterval = null;
        otpDailyLimitReached = false;
        loginButton.disabled = cooldownInterval !== null;
        if (cooldownInterval === null) setLinkDisabled(resendLink, false);
        if (resetCooldownInterval === null) setLinkDisabled(resendResetLink, false);
    }, 1000);
}

function setMessage(text, isError) {
    message.style.display = text ? "block" : "none";
    message.textContent = text || "";
    message.className = "message" + (isError ? " error" : " success");
}

function setResetMessage(text, isError) {
    resetMessage.style.display = text ? "block" : "none";
    resetMessage.textContent = text || "";
    resetMessage.className = "message" + (isError ? " error" : " success");
}

function showGroup(id, visible) {
    const group = document.getElementById(id);
    group.style.display = visible ? "flex" : "none";
    group.querySelectorAll("input, select, textarea").forEach(function(field) {
        field.required = visible;
    });
}

function showResetPasswordFields(visible) {
    ["newPasswordGroup", "confirmNewPasswordGroup"].forEach(function(id) {
        const group = document.getElementById(id);
        group.style.display = visible ? "flex" : "none";
        group.querySelector("input").required = visible;
    });
}

function clearCooldowns() {
    if (cooldownInterval) window.clearInterval(cooldownInterval);
    if (resetCooldownInterval) window.clearInterval(resetCooldownInterval);
    cooldownInterval = null;
    resetCooldownInterval = null;
}

function setMode(mode) {
    authMode = mode;
    resetVerified = false;
    otpDailyLimitReached = false;
    loginButton.disabled = false;
    setLinkDisabled(resendLink, false);
    setLinkDisabled(resendResetLink, false);
    clearCooldowns();
    if (dailyLimitInterval) window.clearInterval(dailyLimitInterval);
    dailyLimitInterval = null;
    loginForm.style.display = "flex";
    otpSection.style.display = "none";
    resetSection.style.display = "none";
    authToggleWrap.style.display = "block";
    authModeLinks.style.display = "flex";
    otpInput.value = "";
    setMessage("", false);
    setResetMessage("", false);
    showResetPasswordFields(false);
    resetButton.textContent = "VERIFY OTP";
    AUTH_FIELDS.forEach(function(id) { showGroup(id, false); });

    if (mode === "student") {
        showGroup("studentEmailGroup", true);
        showGroup("studentPasswordGroup", true);
        authHeading.textContent = "Student Sign In";
        authSubtitle.textContent = "Sign in with your registered email and password.";
        loginButton.textContent = "SIGN IN";
        toggleAuthMode.textContent = "New student? Register here";
        forgotPasswordLink.style.display = "inline";
        staffLoginLink.style.display = "inline";
        staffLoginLink.textContent = "Staff sign in";
    } else if (mode === "staff") {
        showGroup("staffEnrollmentGroup", true);
        showGroup("staffMobileGroup", true);
        authHeading.textContent = "Staff Sign In";
        authSubtitle.textContent = "Enter your staff enrollment number and registered mobile to receive an OTP.";
        loginButton.textContent = "SEND OTP";
        authToggleWrap.style.display = "none";
        forgotPasswordLink.style.display = "none";
        staffLoginLink.style.display = "inline";
        staffLoginLink.textContent = "Student sign in";
    } else if (mode === "register") {
        ["regNameGroup", "regEmailGroup", "regEnrollmentGroup", "regMobileGroup", "regBatchGroup",
            "regTenthGroup", "regTwelfthGroup", "regPasswordGroup", "regPasswordConfirmGroup"]
            .forEach(function(id) { showGroup(id, true); });
        authHeading.textContent = "New Student Registration";
        authSubtitle.textContent = "Register with your @liet.in email and create a password.";
        loginButton.textContent = "SEND OTP";
        toggleAuthMode.textContent = "Already registered? Sign in";
        authModeLinks.style.display = "none";
    } else {
        showGroup("studentEmailGroup", true);
        authHeading.textContent = "Reset Password";
        authSubtitle.textContent = "Enter your registered email to receive a password reset code.";
        loginButton.textContent = "SEND RESET CODE";
        toggleAuthMode.textContent = "Back to student sign in";
        authModeLinks.style.display = "none";
    }
}

function batchRange() {
    const start = regBatchStart.value.trim();
    const end = regBatchEnd.value.trim();
    if (!/^[0-9]{4}$/.test(start) || !/^[0-9]{4}$/.test(end) || Number(start) > Number(end)) return "";
    return start + "-" + end;
}

function validLietEmail(email) {
    return /^[^@\s]+@liet\.in$/i.test(email.trim());
}

function validPasswordLength(password) {
    return new TextEncoder().encode(password).length <= 72;
}

async function postJson(url, data) {
    const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify(data)
    });
    const result = await response.json().catch(function() { return {}; });
    return { ok: response.ok, status: response.status, result: result };
}

function startCooldown(link, countdown, duration, intervalName) {
    const interval = intervalName === "reset" ? resetCooldownInterval : cooldownInterval;
    if (interval) window.clearInterval(interval);
    let seconds = duration;
    if (intervalName === "otp") loginButton.disabled = true;
    setLinkDisabled(link, true);
    countdown.textContent = "Resend available in " + seconds + "s";
    const timer = window.setInterval(function() {
        seconds -= 1;
        if (seconds <= 0) {
            window.clearInterval(timer);
            if (!otpDailyLimitReached) setLinkDisabled(link, false);
            if (intervalName === "otp" && !otpDailyLimitReached) loginButton.disabled = false;
            countdown.textContent = "";
            if (intervalName === "reset") resetCooldownInterval = null;
            else cooldownInterval = null;
            return;
        }
        countdown.textContent = "Resend available in " + seconds + "s";
    }, 1000);
    if (intervalName === "reset") resetCooldownInterval = timer;
    else cooldownInterval = timer;
}

function showOtpStep(mode, data, messageText, response) {
    pendingOtpMode = mode;
    pendingOtpData = data;
    loginForm.style.display = "none";
    resetSection.style.display = "none";
    otpSection.style.display = "block";
    authToggleWrap.style.display = "none";
    authModeLinks.style.display = "none";
    otpSentMessage.className = "message success";
    otpDailyLimitReached = applyDailyLimit(response);
    otpSentMessage.textContent = otpMessageWithAttempts(messageText, response);
    otpInput.value = "";
    startCooldown(resendLink, document.getElementById("resendCountdown"), 60, "otp");
    otpInput.focus();
}

function registrationData() {
    const password = regPassword.value;
    if (!validLietEmail(regEmail.value)) throw new Error("Registration is only open for valid @liet.in email addresses.");
    if (!/^[a-zA-Z0-9]+$/.test(regEnrollment.value.trim())) throw new Error("Enter a valid enrollment number.");
    if (!/^[0-9]{10}$/.test(regMobile.value.trim())) throw new Error("Enter a valid 10-digit mobile number.");
    if (!batchRange()) throw new Error("Enter a valid batch range with the start year before the end year.");
    const tenth = Number(regTenth.value);
    const twelfth = Number(regTwelfth.value);
    if (!Number.isFinite(tenth) || tenth < 0 || tenth > 100) throw new Error("Enter a valid 10th percentage between 0 and 100.");
    if (!Number.isFinite(twelfth) || twelfth < 0 || twelfth > 100) throw new Error("Enter a valid 12th percentage between 0 and 100.");
    if (password.length < 8) throw new Error("Password must be at least 8 characters.");
    if (!validPasswordLength(password)) throw new Error("Password is too long. Use at most 72 UTF-8 bytes.");
    if (password !== regPasswordConfirm.value) throw new Error("Passwords do not match.");
    return {
        name: regName.value.trim(),
        email: regEmail.value.trim().toLowerCase(),
        enrollmentno: regEnrollment.value.trim(),
        mobile: regMobile.value.trim(),
        batch: batchRange(),
        tenthPercentage: tenth,
        twelfthPercentage: twelfth,
        password: password
    };
}

async function submitStudentLogin() {
    const response = await postJson("/api/auth/student/login", {
        email: studentEmail.value.trim().toLowerCase(),
        password: studentPassword.value
    });
    if (!response.ok) {
        setMessage(response.result.message || "Invalid email or password.", true);
        return;
    }
    window.location.href = "/dashboard.html";
}

async function requestOtp(mode, data, isResend) {
    const endpoint = mode === "register" ? "/api/auth/register/send-otp" : "/api/auth/send-otp";
    if (isResend) setLinkDisabled(resendLink, true);
    const response = await postJson(endpoint, data);
    if (!response.ok) {
        const errorMessage = response.result.message || "OTP could not be sent. Please try again after some time.";
        const dailyLimit = applyDailyLimit(response.result);
        const displayMessage = otpMessageWithAttempts(errorMessage, response.result);
        if (otpSection.style.display === "block") {
            otpSentMessage.className = "message error";
            otpSentMessage.textContent = displayMessage;
        } else {
            setMessage(displayMessage, true);
        }
        if (response.status === 429 && !dailyLimit && response.result.retryAfterSeconds > 0
                && !errorMessage.includes("already being processed")) {
            startCooldown(resendLink, document.getElementById("resendCountdown"),
                response.result.retryAfterSeconds, "otp");
        } else if (isResend && !dailyLimit) {
            setLinkDisabled(resendLink, false);
        }
        return false;
    }
    showOtpStep(mode, data, response.result.message || "OTP sent. Check your email.", response.result);
    return true;
}

async function sendForgotOtp(email, isResend) {
    if (isResend) setLinkDisabled(resendResetLink, true);
    const response = await postJson("/api/auth/forgot-password/send-otp", { email: email });
    if (!response.ok) {
        const message = response.result.message || "OTP could not be sent. Please try again after some time.";
        const dailyLimit = applyDailyLimit(response.result);
        const displayMessage = otpMessageWithAttempts(message, response.result);
        if (isResend) {
            const target = document.getElementById("resetCountdown");
            if (response.status === 429 && !dailyLimit && response.result.retryAfterSeconds > 0
                    && !message.includes("already being processed")) {
                startCooldown(resendResetLink, target, response.result.retryAfterSeconds, "reset");
            } else if (!dailyLimit) {
                setLinkDisabled(resendResetLink, false);
            }
            document.getElementById("resetSentMessage").textContent = displayMessage;
            document.getElementById("resetSentMessage").className = "message error";
        } else {
            setMessage(displayMessage, true);
        }
        return false;
    }

    otpDailyLimitReached = applyDailyLimit(response.result);
    if (isResend) {
        resetVerified = false;
        showResetPasswordFields(false);
        resetOtp.value = "";
        newPassword.value = "";
        confirmNewPassword.value = "";
        resetButton.textContent = "VERIFY OTP";
        const sentBox = document.getElementById("resetSentMessage");
        sentBox.textContent = otpMessageWithAttempts(response.result.message, response.result);
        sentBox.className = "message success";
        startCooldown(resendResetLink, document.getElementById("resetCountdown"), 60, "reset");
    } else {
        resetVerified = false;
        showResetPasswordFields(false);
        resetOtp.value = "";
        resetButton.textContent = "VERIFY OTP";
        resetEmail.value = email;
        loginForm.style.display = "none";
        otpSection.style.display = "none";
        resetSection.style.display = "block";
        authToggleWrap.style.display = "none";
        authModeLinks.style.display = "none";
        const sentBox = document.getElementById("resetSentMessage");
        sentBox.textContent = otpMessageWithAttempts(response.result.message, response.result);
        sentBox.className = "message success";
        startCooldown(resendResetLink, document.getElementById("resetCountdown"), 60, "reset");
        resetOtp.focus();
    }
    return true;
}

async function submitAuthForm() {
    setMessage("", false);
    loginButton.disabled = true;
    const originalText = loginButton.textContent;
    loginButton.textContent = "Please wait...";
    try {
        if (authMode === "student") {
            await submitStudentLogin();
        } else if (authMode === "forgot") {
            const email = studentEmail.value.trim().toLowerCase();
            if (!email) throw new Error("Enter your registered email address.");
            await sendForgotOtp(email, false);
        } else if (authMode === "staff") {
            const data = {
                enrollmentno: document.getElementById("enrollmentno").value.trim(),
                mobile: document.getElementById("mobile").value.trim()
            };
            if (!/^[a-zA-Z0-9]{1,50}$/.test(data.enrollmentno)) throw new Error("Enter a valid staff enrollment number.");
            if (!/^[0-9]{10}$/.test(data.mobile)) throw new Error("Enter a valid 10-digit mobile number.");
            await requestOtp("staff", data);
        } else {
            if (!regName.value.trim()) throw new Error("Enter your full name.");
            const data = registrationData();
            await requestOtp("register", data);
        }
    } catch (error) {
        setMessage(error.message || "Something went wrong. Please try again.", true);
    } finally {
        loginButton.disabled = otpDailyLimitReached || cooldownInterval !== null;
        loginButton.textContent = originalText;
    }
}

async function verifyOtp() {
    otpButton.disabled = true;
    const originalText = otpButton.textContent;
    otpButton.textContent = "Verifying...";
    try {
        const data = Object.assign({}, pendingOtpData, { otp: otpInput.value.trim() });
        const endpoint = pendingOtpMode === "register" ? "/api/auth/register/verify" : "/api/auth/verify-otp";
        const response = await postJson(endpoint, data);
        if (!response.ok) {
            otpSentMessage.className = "message error";
            otpSentMessage.textContent = response.result.message || "Invalid or expired OTP.";
            return;
        }
        if (pendingOtpMode === "register") {
            setMode("student");
            studentEmail.value = pendingOtpData.email;
            setMessage(response.result.message || "Registration successful. Sign in with your email and password.", false);
        } else if (response.result.role === "ADMIN") {
            window.location.href = "/admin-dashboard.html";
        } else {
            window.location.href = "/dashboard.html";
        }
    } catch (error) {
        otpSentMessage.className = "message error";
        otpSentMessage.textContent = "Could not verify the OTP. Please try again.";
    } finally {
        otpButton.disabled = false;
        otpButton.textContent = originalText;
    }
}

async function resetStudentPassword(event) {
    event.preventDefault();
    setResetMessage("", false);
    if (!resetVerified) {
        resetButton.disabled = true;
        resetButton.textContent = "Verifying...";
        try {
            const response = await postJson("/api/auth/forgot-password/verify-otp", {
                email: resetEmail.value,
                otp: resetOtp.value.trim()
            });
            if (!response.ok) {
                setResetMessage(response.result.message || "Invalid or expired OTP.", true);
                return;
            }
            resetVerified = true;
            showResetPasswordFields(true);
            setResetMessage(response.result.message || "Code verified. Create your new password.", false);
        } catch (error) {
            setResetMessage("Could not verify the OTP. Please try again.", true);
        } finally {
            resetButton.disabled = false;
            resetButton.textContent = resetVerified ? "RESET PASSWORD" : "VERIFY OTP";
        }
        return;
    }

    if (newPassword.value.length < 8) {
        setResetMessage("Password must be at least 8 characters.", true);
        return;
    }
    if (!validPasswordLength(newPassword.value)) {
        setResetMessage("Password is too long. Use at most 72 UTF-8 bytes.", true);
        return;
    }
    if (newPassword.value !== confirmNewPassword.value) {
        setResetMessage("Passwords do not match.", true);
        return;
    }

    resetButton.disabled = true;
    const originalText = resetButton.textContent;
    resetButton.textContent = "Resetting...";
    try {
        const response = await postJson("/api/auth/forgot-password/reset", {
            email: resetEmail.value,
            otp: resetOtp.value.trim(),
            newPassword: newPassword.value
        });
        if (!response.ok) {
            setResetMessage(response.result.message || "Invalid or expired OTP.", true);
            return;
        }
        const email = resetEmail.value;
        setMode("student");
        studentEmail.value = email;
        setMessage(response.result.message || "Password reset. Sign in with your new password.", false);
    } catch (error) {
        setResetMessage("Could not reset the password. Please try again.", true);
    } finally {
        resetButton.disabled = false;
        resetButton.textContent = resetVerified ? "RESET PASSWORD" : originalText;
    }
}

loginForm.addEventListener("submit", function(event) {
    event.preventDefault();
    submitAuthForm();
});

otpForm.addEventListener("submit", function(event) {
    event.preventDefault();
    verifyOtp();
});

resetForm.addEventListener("submit", resetStudentPassword);

resendLink.addEventListener("click", function(event) {
    event.preventDefault();
    if (resendLink.getAttribute("aria-disabled") === "true" || !pendingOtpData) return;
    otpInput.value = "";
    requestOtp(pendingOtpMode, pendingOtpData, true);
});

resendResetLink.addEventListener("click", function(event) {
    event.preventDefault();
    if (resendResetLink.getAttribute("aria-disabled") === "true" || !resetEmail.value) return;
    sendForgotOtp(resetEmail.value, true);
});

backLink.addEventListener("click", function(event) {
    event.preventDefault();
    setMode(pendingOtpMode === "register" ? "register" : "staff");
});

document.getElementById("backToLoginFromReset").addEventListener("click", function(event) {
    event.preventDefault();
    setMode("student");
});

toggleAuthMode.addEventListener("click", function(event) {
    event.preventDefault();
    if (authMode === "student") setMode("register");
    else setMode("student");
});

forgotPasswordLink.addEventListener("click", function(event) {
    event.preventDefault();
    setMode("forgot");
});

staffLoginLink.addEventListener("click", function(event) {
    event.preventDefault();
    setMode(authMode === "staff" ? "student" : "staff");
});

setMode("student");