const /** @type {HTMLFormElement} */ form = document.getElementById("login-form"), /** @type {HTMLInputElement} */ passwordInput = form.querySelector("input[name=\"password\"]"),
/** @type {HTMLParagraphElement} */ error = document.getElementById("error-display"), /** @type {HTMLHeaderElement} */ title = document.getElementById("title"),
/** @type {HTMLButtonElement} */ submitButton = document.querySelector("#login-form button[type=\"submit\"]");
form.addEventListener("submit", async (/** @type {SubmitEvent} */ event) => {
    event.preventDefault();

    const data = new FormData(form);
    const res = await fetch("/api/" + (login ? "login" : "signup"), {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            username: data.get("username"),
            password: data.get("password")
        })
    });

    if (login) {
        if (res.status === 401) return showError("Incorrect password.");
        else if (res.status === 404) return showError("Incorrect username.");
        else if (res.status === 500) return showError("An internal server error occurred. Please try again in a few minutes.");
    } else {
        if (res.status === 401 || res.status === 500) return showError("That username is already taken. Please try another.");
    }

    // TODO: Here, redirect to the profile page and load that stuff
    document.getElementById("window").querySelectorAll("button").forEach(element => element.onclick = () => {});
    form.querySelectorAll("input").forEach(element => element.disabled = true);
    error.style.color = "green";
    error.textContent = "Successful " + (login ? "sign in" : "sign up") + ". Redirecting...";
    error.style.display = "block";
    
    window.location.href = new URLSearchParams(window.location.search).get("redirect") || "/profile";
});

let login = true, displayedError = null;
function switchMethod(switcherElement) {
    if (login) {
        login = false;
        title.textContent = "Sign Up";
        submitButton.textContent = "Sign Up";
        switcherElement.textContent = "Have an account? Login here";
    } else {
        login = true;
        title.textContent = "Sign In";
        submitButton.textContent = "Sign In";
        switcherElement.textContent = "New? Sign up here";
    }
}

/**
 * Shows/Hides the password
 * 
 * @param {HTMLButtonElement} buttonElement The button element that was clicked
 */
function showPassword(buttonElement) {
    if (passwordInput.type === "password") {
        passwordInput.type = "text";
        buttonElement.textContent = "Hide";
    } else {
        passwordInput.type = "password";
        buttonElement.textContent = "Show";
    }
}

/**
 * Displays a error on the error display
 * 
 * @param {string} errorMsg The error message
 */
function showError(errorMsg) {
    if (displayedError !== null) clearTimeout(displayedError);
    error.textContent = errorMsg;
    error.style.display = "block";
    displayedError = setTimeout(() => {
        error.style.display = "none";
    }, 10000);
}