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

let currentEnrollment = "";
let currentMobile = "";

function setMessage(text, isError) {
    message.textContent = text;
    message.className = "message " + (isError ? "error" : "success");
}

async function sendOtp() {
    setMessage("", false);
    loginButton.disabled = true;
    loginButton.textContent = "Sending...";

    const data = {
        enrollmentno: document.getElementById("enrollmentno").value,
        mobile: document.getElementById("mobile").value
    };

    try {
        const response = await fetch("/api/auth/send-otp", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (response.ok && result.otpRequired) {
            currentEnrollment = data.enrollmentno;
            currentMobile = data.mobile;
            loginForm.style.display = "none";
            otpSection.style.display = "block";
            otpSentMessage.textContent = result.message + (result.email ? " (" + result.email + ")" : "");
            otpInput.focus();
        } else {
            setMessage(result.message || "Failed to send OTP. Please try again.", true);
        }
    } catch (error) {
        setMessage("Something went wrong. Please try again.", true);
    } finally {
        loginButton.disabled = false;
        loginButton.textContent = "Send OTP";
    }
}

async function verifyOtp() {
    setMessage("", false);
    otpButton.disabled = true;
    otpButton.textContent = "Verifying...";

    const data = {
        enrollmentno: currentEnrollment,
        mobile: currentMobile,
        otp: otpInput.value
    };

    try {
        const response = await fetch("/api/auth/verify-otp", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (response.ok) {
            if (result.role === "ADMIN") {
                window.location.href = "/admin-dashboard.html";
            } else if (result.role === "COORDINATOR") {
                window.location.href = "/coordinator-dashboard.html";
            } else {
                window.location.href = "/dashboard.html";
            }
        } else {
            setMessage(result.message || "Invalid OTP. Please try again.", true);
        }
    } catch (error) {
        setMessage("Something went wrong. Please try again.", true);
    } finally {
        otpButton.disabled = false;
        otpButton.textContent = "Verify OTP";
    }
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
