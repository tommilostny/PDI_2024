function drawMandelbrot(canvasId, width, height, pixels, maxIterations) {
    const canvas = document.getElementById(canvasId);
    const context = canvas.getContext("2d");
    const imageData = context.createImageData(width, height);

    for (let i = 0; i < pixels.length; i++) {
        const color = pixels[i] === maxIterations ? 0 : 255 - (pixels[i] * 255 / maxIterations);
        imageData.data[i * 4] = color;
        imageData.data[i * 4 + 1] = color;
        imageData.data[i * 4 + 2] = color;
        imageData.data[i * 4 + 3] = 255;
    }

    context.putImageData(imageData, 0, 0);
}
