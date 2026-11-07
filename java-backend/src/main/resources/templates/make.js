const thumbnailUploadInput = document.querySelector("label input[name=thumbnail]"), thumbnailUploadText = thumbnailUploadInput.previousSibling.previousSibling, thumbnailPreviewElement = thumbnailUploadInput.nextSibling.nextSibling,
videoUploadInput = document.querySelector("label input[name=file]"), videoUploadText = videoUploadInput.previousSibling.previousSibling, videoPreviewElement = videoUploadInput.nextSibling.nextSibling;

thumbnailUploadInput.onchange = async () => {
    if (thumbnailUploadInput.files.length !== 0) {
        const file = thumbnailUploadInput.files[0];
        thumbnailUploadText.style.display = "none";
        thumbnailPreviewElement.style.display = "";
        thumbnailPreviewElement.src = "data:" + file.type + ";base64," + (await file.bytes()).toBase64();
    } else {
        thumbnailUploadText.style.display = "";
        thumbnailPreviewElement.style.display = "none";
    }
}

videoUploadInput.onchange = async () => {
    if (videoUploadInput.files.length !== 0) {
        const file = videoUploadInput.files[0];
        videoUploadText.style.display = "none";
        videoPreviewElement.style.display = "";
        videoPreviewElement.src = "data:" + file.tyoe + ";base64," + (await file.bytes()).toBase64();
    } else {
        videoUploadText.style.display = "";
        videoPreviewElement.style.display = "none";
    }
}

const /** @type {HTMLFormElement} */ form = document.getElementById("upload"), statusElement = document.getElementById("status");
form.addEventListener("submit", async (event) => {
    event.preventDefault();

    statusElement.style.color = "green";
    statusElement.textContent = "Uploading...";

    const formData = new FormData(form);
    const res = await fetch("/api/video/new", {
        method: "POST",
        body: formData
    });

    if (res.ok) {
        statusElement.textContent = "Uploaded!";
        form.querySelectorAll("input, textarea").forEach(input => input.value = "");
        form.querySelectorAll("img, video").forEach(preview => preview.style.display = "none");
        thumbnailUploadText.style.display = "";
        videoUploadText.style.display = "";
    } else {
        statusElement.style.color = "red";
        statusElement.textContent = "Upload failed. Please try again.";
    }

    const videoId = await res.text();
    // TODO: In a future update, display a popup to view this
});