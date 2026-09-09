import {
    FilesetResolver,
    PoseLandmarker,
} from "../mediapipe/vision_bundle.js";

let landmarker = null;

async function init(modelPath, wasmUrl) {
    const vision = await FilesetResolver.forVisionTasks(wasmUrl);

    try {
        landmarker = await PoseLandmarker.createFromOptions(vision, {
            baseOptions: { modelAssetPath: modelPath, delegate: "GPU" },
            runningMode: "VIDEO",
            numPoses: 1,
        });
    } catch (err) {
        landmarker = await PoseLandmarker.createFromOptions(vision, {
            baseOptions: { modelAssetPath: modelPath, delegate: "CPU" },
            runningMode: "VIDEO",
            numPoses: 1,
        });
    }
}

self.onmessage = function (event) {
    const msg = event.data;

    if (msg.type === "init") {
        init(msg.modelPath, msg.wasmUrl)
            .then(function () {
                self.postMessage({ type: "ready" });
            })
            .catch(function (err) {
                self.postMessage({ type: "error", message: String(err) });
            });
        return;
    }

    if (msg.type !== "detect" || !landmarker) {
        return;
    }

    try {
        const result = landmarker.detectForVideo(msg.bitmap, msg.timestamp);

        const landmarks = result.landmarks && result.landmarks[0]
            ? result.landmarks[0].map(function (l) {
                return [l.x, l.y, l.z, l.visibility];
            })
            : null;

        self.postMessage({ type: "result", id: msg.id, landmarks: landmarks });
    } catch (err) {
        self.postMessage({ type: "result", id: msg.id, landmarks: null });
    } finally {
        if (msg.bitmap) {
            msg.bitmap.close();
        }
    }
};