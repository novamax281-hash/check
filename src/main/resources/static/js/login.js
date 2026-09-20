const loginForm = document.getElementById("loginForm");
const loginButton = document.getElementById("loginButton");
const otpSection = document.getElementById("otpSection");
const otpForm = document.getElementById("otpForm");
const otpButton = document.getElementById("otpButton");
const otpInput = document.getElementById("otp");
const otpSentMessage = document.getElementById("otpSentMessage");
const resendLink = document.getElementById("resendOtp");
const backLink = document.getElementById("backToLogin");
const message = document.getElementById("message");
const toggleAuthMode = document.getElementById("toggleAuthMode");
const authHeading = document.getElementById("authHeading");
const authSubtitle = document.getElementById("authSubtitle");
const regNameGroup = document.getElementById("regNameGroup");
const regEmailGroup = document.getElementById("regEmailGroup");
const regBatchGroup = document.getElementById("regBatchGroup");
const regTenthGroup = document.getElementById("regTenthGroup");
const regTwelfthGroup = document.getElementById("regTwelfthGroup");
const regName = document.getElementById("regName");
const regEmail = document.getElementById("regEmail");
const regBatchStart = document.getElementById("regBatchStart");
const regBatchEnd = document.getElementById("regBatchEnd");
const regTenth = document.getElementById("regTenth");
const regTwelfth = document.getElementById("regTwelfth");

let currentEnrollment = "";
let currentMobile = "";
let authMode = "login";

function setMessage(text, isError) {
    if (!text) {
        message.style.display = "none";
        message.textContent = "";
        message.className = "message";
        return;
    }
    message.style.display = "block";
    message.textContent = text;
    message.className = "message " + (isError ? "error" : "success");
}

function setFormMode(mode) {
    authMode = mode;
    otpSection.style.display = "none";
    loginForm.style.display = "block";
    otpInput.value = "";
    setMessage("", false);

    if (mode === "register") {
        authHeading.textContent = "New Student Registration";
        authSubtitle.textContent = "Register with your college email. Only @liet.in addresses and a 10-digit mobile number are accepted.";
        loginButton.textContent = "SEND OTP";
        regNameGroup.style.display = "block";
        regEmailGroup.style.display = "block";
        regBatchGroup.style.display = "block";
        regTenthGroup.style.display = "block";
        regTwelfthGroup.style.display = "block";
        toggleAuthMode.textContent = "Already registered? Sign in";
    } else {
        authHeading.textContent = "Student & Staff Sign In";
        authSubtitle.textContent = "Use your enrollment number (students) or staff enrollment, plus your registered mobile number.";
        loginButton.textContent = "SEND OTP";
        regNameGroup.style.display = "none";
        regEmailGroup.style.display = "none";
        regBatchGroup.style.display = "none";
        regTenthGroup.style.display = "none";
        regTwelfthGroup.style.display = "none";
        toggleAuthMode.textContent = "New student? Register here";
    }
}

function lietEmailValid(email) {
    return typeof email === "string" && email.trim().toLowerCase().endsWith("@liet.in");
}

function batchRange() {
    const start = regBatchStart.value.trim();
    const end = regBatchEnd.value.trim();
    if (!/^[0-9]{4}$/.test(start) || !/^[0-9]{4}$/.test(end)) return "";
    if (Number(start) > Number(end)) return "";
    return start + "-" + end;
}

function sendOtp() {
    setMessage("Sending OTP...", false);
    loginButton.disabled = true;
    loginButton.textContent = "Sending...";

    let url = "/api/auth/send-otp";
    const data = {
        enrollmentno: document.getElementById("enrollmentno").value,
        mobile: document.getElementById("mobile").value
    };

    if (authMode === "register") {
        const name = regName.value.trim();
        const email = regEmail.value.trim();
        if (!name) {
            setMessage("Please enter your full name.", true);
            loginButton.disabled = false;
            loginButton.textContent = "SEND OTP";
            return;
        }
        if (!lietEmailValid(email)) {
            setMessage("Registration is only open for @liet.in email addresses.", true);
            loginButton.disabled = false;
            loginButton.textContent = "SEND OTP";
            return;
        }
        if (!/^[0-9]{10}$/.test(data.mobile)) {
            setMessage("Enter a valid 10-digit mobile number.", true);
            loginButton.disabled = false;
            loginButton.textContent = "SEND OTP";
            return;
        }
        const batch = batchRange();
        if (!batch) {
            setMessage("Enter a valid batch (4-digit start and end year, start before end).", true);
            loginButton.disabled = false;
            loginButton.textContent = "SEND OTP";
            return;
        }
        const tenth = Number(regTenth.value);
        const twelfth = Number(regTwelfth.value);
        if (regTenth.value.trim() === "" || Number.isNaN(tenth) || tenth < 0 || tenth > 100) {
            setMessage("Enter a valid 10th percentage (0-100).", true);
            loginButton.disabled = false;
            loginButton.textContent = "SEND OTP";
            return;
        }
        if (regTwelfth.value.trim() === "" || Number.isNaN(twelfth) || twelfth < 0 || twelfth > 100) {
            setMessage("Enter a valid 12th percentage (0-100).", true);
            loginButton.disabled = false;
            loginButton.textContent = "SEND OTP";
            return;
        }
        url = "/api/auth/register/send-otp";
        data.name = name;
        data.email = email;
        data.batch = batch;
        data.tenthPercentage = tenth;
        data.twelfthPercentage = twelfth;
    }

    fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data)
    })
        .then(function (response) {
            return response.json().then(function (result) {
                return { ok: response.ok, result: result };
            });
        })
        .then(function (wrapper) {
            if (wrapper.ok && wrapper.result.otpRequired) {
                currentEnrollment = data.enrollmentno;
                currentMobile = data.mobile;
                loginForm.style.display = "none";
                otpSection.style.display = "block";
                otpSentMessage.textContent = wrapper.result.message + (wrapper.result.email ? " (" + wrapper.result.email + ")" : "");
                otpInput.focus();
            } else {
                setMessage(wrapper.result.message || "Invalid details. Please check your input.", true);
            }
        })
        .catch(function () {
            setMessage("Server error. Please try again.", true);
        })
        .finally(function () {
            loginButton.disabled = false;
            loginButton.textContent = "SEND OTP";
        });
}

function verifyOtp() {
    setMessage("", false);
    otpButton.disabled = true;
    otpButton.textContent = "Verifying...";

    const data = {
        enrollmentno: currentEnrollment,
        mobile: currentMobile,
        otp: otpInput.value
    };

    let url = "/api/auth/verify-otp";
    if (authMode === "register") {
        url = "/api/auth/register/verify";
        data.name = regName.value.trim();
        data.email = regEmail.value.trim();
        data.batch = batchRange();
        data.tenthPercentage = Number(regTenth.value);
        data.twelfthPercentage = Number(regTwelfth.value);
    }

    fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data)
    })
        .then(function (response) {
            return response.json().then(function (result) {
                return { ok: response.ok, result: result };
            });
        })
        .then(function (wrapper) {
            if (!wrapper.ok) {
                setMessage(wrapper.result.message || "Invalid OTP. Please try again.", true);
                return;
            }
            if (authMode === "register") {
                setMessage(wrapper.result.message || "Registration successful. Sign in with your enrollment number and mobile.", false);
                document.getElementById("mobile").value = currentMobile;
                setFormMode("login");
            } else if (wrapper.result.role === "ADMIN") {
                window.location.href = "/admin-dashboard.html";
            } else {
                window.location.href = "/dashboard.html";
            }
        })
        .catch(function () {
            setMessage("Something went wrong. Please try again.", true);
        })
        .finally(function () {
            otpButton.disabled = false;
            otpButton.textContent = "VERIFY OTP";
        });
}

loginForm.addEventListener("submit", function (event) {
    event.preventDefault();
    sendOtp();
});

otpForm.addEventListener("submit", function (event) {
    event.preventDefault();
    verifyOtp();
});

resendLink.addEventListener("click", function (event) {
    event.preventDefault();
    otpInput.value = "";
    sendOtp();
});

backLink.addEventListener("click", function (event) {
    event.preventDefault();
    otpSection.style.display = "none";
    loginForm.style.display = "block";
    otpInput.value = "";
    setMessage("", false);
});

toggleAuthMode.addEventListener("click", function (event) {
    event.preventDefault();
    setFormMode(authMode === "login" ? "register" : "login");
});