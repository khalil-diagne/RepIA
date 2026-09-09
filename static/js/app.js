import { EXERCICES, EXO_META } from "./exercices.js";
import { DetecteurExercice } from "./detecteur.js";

const MODELE_URL = "/pose_landmarker_lite.task";
const WASM_URL = "/static/mediapipe/wasm";
const BUNDLE_LOCAL = "../mediapipe/vision_bundle.js";
const BUNDLE_CDN = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@1.0.1/vision_bundle.mjs";

const WORKER_TIMEOUT_MS = 12000;

const RES_MAX = 640;
const RES_MIN = 320;
const RES_PAS = 64;
const SEUIL_DIMINUER = 50;
const SEUIL_AUGMENTER = 22;

const video = document.getElementById("video");
const canvas = document.getElementById("canvas");
const ctx = canvas.getContext("2d");

let detecteur = new DetecteurExercice("pompes");
let pose = null;

let stream = null;
let runToken = 0;
let derniereFacing = "user";

let worker = null;
let workerPret = false;
let workerOccupe = false;
let workerEchec = false;
let landmarker = null;
let fallbackLance = false;
let workerTimer = null;

let tailleCap = RES_MAX;
let moyenInf = 0;
let nbMesures = 0;
let dernierEnvoiAt = 0;
let compteurFrames = 0;
let diviseurFrames = 1;
let dernierResultAt = 0;

function showError(msg) {
    document.getElementById("cam-banner").textContent = msg;
    document.getElementById("cam-banner").classList.add("visible");
}

function clearError() {
    document.getElementById("cam-banner").classList.remove("visible");
}

function setStatut(texte, enLigne) {
    document.getElementById("status-text").textContent = texte;
    document.getElementById("dot").className = "dot " + (enLigne ? "online" : "offline");
}

// ============================================================
// CARTES D'EXERCICES
// ============================================================

function buildCards() {
    const box = document.getElementById("exercises");
    box.innerHTML = "";
    Object.keys(EXERCICES).forEach(function (id) {
        const cfg = EXERCICES[id];
        const meta = EXO_META[id] || { icon: "🏋️", desc: "" };
        const div = document.createElement("div");
        div.className = "exo" + (id === detecteur.typeExo ? " active" : "");
        div.dataset.id = id;
        div.innerHTML =
            '<div class="icon">' + meta.icon + "</div>" +
            '<div class="name">' + cfg.nom + "</div>" +
            '<div class="desc">' + meta.desc + "</div>";
        div.addEventListener("click", function () {
            selectExo(id);
        });
        box.appendChild(div);
    });
}

function selectExo(id) {
    if (id === detecteur.typeExo) return;

    detecteur = new DetecteurExercice(id);
    document.getElementById("exo-title").textContent = detecteur.nom;

    document.querySelectorAll(".exo").forEach(function (el) {
        el.classList.toggle("active", el.dataset.id === id);
    });

    updateStatsUI(detecteur.stats());
}

function resetCompteur() {
    detecteur.reset();
    pose = null;
    updateStatsUI(detecteur.stats());
}

// ============================================================
// STATS (UI)
// ============================================================

function updateStatsUI(st) {
    document.getElementById("compteur").textContent = st.compteur;
    document.getElementById("stage").textContent = st.stage;
    document.getElementById("angle").textContent =
        st.angleCoude != null ? st.angleCoude.toFixed(1) + "°" : "—";
}

function onPose(raw) {
    pose = raw;
    const stats = detecteur.analyser(pose);
    updateStatsUI(stats);
}

// ============================================================
// DESSIN
// ============================================================

function dessinerPose(pose, W, H) {
    if (!pose) return;

    const couleurLigne = "#ff0000";
    const couleurPoint = "#00ff00";
    const couleurMenton = "#ffff00";

    ctx.lineWidth = 3;
    ctx.strokeStyle = couleurLigne;
    ctx.beginPath();
    ctx.moveTo(pose[12].x * W, pose[12].y * H);
    ctx.lineTo(pose[14].x * W, pose[14].y * H);
    ctx.lineTo(pose[16].x * W, pose[16].y * H);
    ctx.moveTo(pose[11].x * W, pose[11].y * H);
    ctx.lineTo(pose[13].x * W, pose[13].y * H);
    ctx.lineTo(pose[15].x * W, pose[15].y * H);
    ctx.stroke();

    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(pose[12].x * W, pose[12].y * H);
    ctx.lineTo(pose[24].x * W, pose[24].y * H);
    ctx.lineTo(pose[28].x * W, pose[28].y * H);
    ctx.moveTo(pose[11].x * W, pose[11].y * H);
    ctx.lineTo(pose[23].x * W, pose[23].y * H);
    ctx.lineTo(pose[27].x * W, pose[27].y * H);
    ctx.stroke();

    for (const i of [11, 12, 13, 14, 15, 16, 23, 24, 27, 28]) {
        ctx.fillStyle = couleurPoint;
        ctx.beginPath();
        ctx.arc(pose[i].x * W, pose[i].y * H, 6, 0, 2 * Math.PI);
        ctx.fill();
    }

    if (detecteur.config.menton && pose[0]) {
        ctx.fillStyle = couleurMenton;
        ctx.beginPath();
        ctx.arc(pose[0].x * W, pose[0].y * H, 8, 0, 2 * Math.PI);
        ctx.fill();
    }
}

function dessinerHUD(W) {
    const ratio = Math.min(1, W / 360);
    const boxW = 360 * ratio;
    const boxH = 100 * ratio;
    const st = detecteur.stats();

    ctx.fillStyle = "#000000";
    ctx.fillRect(0, 0, boxW, boxH);

    ctx.fillStyle = "#ffffff";
    ctx.font = Math.round(36 * ratio) + "px sans-serif";
    ctx.fillText(detecteur.nom + " : " + st.compteur, 15 * ratio, 42 * ratio);

    ctx.font = Math.round(26 * ratio) + "px sans-serif";
    ctx.fillText("Position : " + st.stage, 15 * ratio, 76 * ratio);

    if (st.angleCoude != null) {
        ctx.font = Math.round(26 * ratio) + "px sans-serif";
        ctx.fillText(
            "Angle coude : " + st.angleCoude.toFixed(1) + " deg",
            20 * ratio,
            (130 * ratio) + 40
        );
    } else {
        ctx.fillStyle = "#ff0000";
        ctx.font = Math.round(26 * ratio) + "px sans-serif";
        ctx.fillText(
            "Bras non detecte",
            20 * ratio,
            (130 * ratio) + 40
        );
    }
}

function dessiner(W, H) {
    const miroir = derniereFacing === "user";

    if (miroir) {
        ctx.setTransform(-1, 0, 0, 1, W, 0);
    } else {
        ctx.setTransform(1, 0, 0, 1, 0, 0);
    }

    ctx.drawImage(video, 0, 0, W, H);
    dessinerPose(pose, W, H);

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    dessinerHUD(W);
}

// ============================================================
// WORKER (chemin privilegie) + repli thread principal
// ============================================================

function demarrerWorker() {
    try {
        worker = new Worker("/static/js/worker.js", { type: "module" });
    } catch (err) {
        workerEchec = true;
        return;
    }

    worker.onmessage = function (event) {
        const msg = event.data;

        if (msg.type === "ready") {
            workerPret = true;
            if (workerTimer) {
                clearTimeout(workerTimer);
                workerTimer = null;
            }
            setStatut("Détection active", true);
            return;
        }

        if (msg.type === "error") {
            workerEchec = true;
            return;
        }

        if (msg.type === "result") {
            workerOccupe = false;

            const maintenant = performance.now();

            if (dernierEnvoiAt) {
                ajusterAdaptatif(maintenant - dernierEnvoiAt);
                dernierEnvoiAt = 0;
            }

            if (dernierResultAt) {
                const fps = 1000 / (maintenant - dernierResultAt);
                document.getElementById("perf").textContent =
                    tailleCap + "px · " + (diviseurFrames > 1 ? "1/" + diviseurFrames + " · " : "") +
                    Math.round(fps) + " fps";
            }
            dernierResultAt = maintenant;

            const raw = msg.landmarks ? msg.landmarks.map(function (l) {
                return { x: l[0], y: l[1], z: l[2], visibility: l[3] };
            }) : null;

            onPose(raw);
        }
    };

    worker.postMessage({ type: "init", modelPath: MODELE_URL, wasmUrl: WASM_URL });

    workerTimer = setTimeout(function () {
        if (!workerPret) {
            workerEchec = true;
        }
    }, WORKER_TIMEOUT_MS);
}

async function chargerVision() {
    try {
        return await import(BUNDLE_LOCAL);
    } catch (err) {
        console.warn("Bundle local indisponible, repli CDN :", err);
        return await import(BUNDLE_CDN);
    }
}

async function initLandmarkerThread() {
    if (landmarker) return landmarker;

    const visuel = await chargerVision();
    const vision = await visuel.FilesetResolver.forVisionTasks(WASM_URL);

    try {
        landmarker = await visuel.PoseLandmarker.createFromOptions(vision, {
            baseOptions: { modelAssetPath: MODELE_URL, delegate: "GPU" },
            runningMode: "VIDEO",
            numPoses: 1,
        });
    } catch (err) {
        landmarker = await visuel.PoseLandmarker.createFromOptions(vision, {
            baseOptions: { modelAssetPath: MODELE_URL, delegate: "CPU" },
            runningMode: "VIDEO",
            numPoses: 1,
        });
    }

    setStatut("Détection active (repli thread principal)", true);
    return landmarker;
}

function activerRepliThread() {
    if (workerEchec && !fallbackLance) {
        fallbackLance = true;
        initLandmarkerThread()
            .catch(function (err) {
                console.error("Modèle MediaPipe indisponible :", err);
                showError("Impossible de charger le modèle de détection. Vérifie la connexion puis recharge la page.");
                setStatut("Modèle indisponible", false);
            });
    }
}

function envoyerFrameWorker(now) {
    if (workerOccupe) {
        return;
    }

    compteurFrames += 1;
    if (compteurFrames % diviseurFrames !== 0) {
        return;
    }

    createImageBitmap(video, { resizeWidth: tailleCap })
        .then(function (bitmap) {
            if (!workerPret || workerEchec) {
                bitmap.close();
                return;
            }
            workerOccupe = true;
            dernierEnvoiAt = performance.now();
            worker.postMessage(
                { type: "detect", id: now, timestamp: now, bitmap: bitmap },
                [bitmap]
            );
        })
        .catch(function () {});
}

function ajusterAdaptatif(duree) {
    if (duree <= 0) {
        return;
    }

    nbMesures += 1;
    if (nbMesures === 1) {
        moyenInf = duree;
    } else {
        moyenInf = 0.9 * moyenInf + 0.1 * duree;
    }

    if (nbMesures % 30 !== 0) {
        return;
    }

    if (moyenInf > SEUIL_DIMINUER) {
        if (tailleCap > RES_MIN) {
            tailleCap -= RES_PAS;
        } else if (diviseurFrames < 3) {
            diviseurFrames += 1;
        } else {
            return;
        }
    } else if (moyenInf < SEUIL_AUGMENTER) {
        if (diviseurFrames > 1) {
            diviseurFrames -= 1;
        } else if (tailleCap < RES_MAX) {
            tailleCap = Math.min(RES_MAX, tailleCap + RES_PAS);
        } else {
            return;
        }
    } else {
        return;
    }

    nbMesures = 0;
}

function traiterFrameThreadPrincipal(now) {
    activerRepliThread();

    if (!landmarker) {
        return;
    }

    const result = landmarker.detectForVideo(video, now);
    const raw = result.landmarks && result.landmarks[0] ? result.landmarks[0] : null;
    onPose(raw);
}

// ============================================================
// BOUCLE DE RENDERING
// ============================================================

function processFrame(now) {
    const W = video.videoWidth;
    const H = video.videoHeight;
    if (!W || !H || !video.readyState) return;

    if (canvas.width !== W || canvas.height !== H) {
        canvas.width = W;
        canvas.height = H;
    }

    if (workerPret) {
        envoiFrameWorker(now);
        dessiner(W, H);
    } else if (workerEchec) {
        traiterFrameThreadPrincipal(now);
        dessiner(W, H);
    } else {
        canvas.width = W;
        canvas.height = H;
        dessiner(W, H);
    }
}

function startLoop() {
    const token = ++runToken;

    if (typeof video.requestVideoFrameCallback === "function") {
        const step = function (now) {
            if (token !== runToken) return;
            processFrame(now);
            video.requestVideoFrameCallback(step);
        };
        video.requestVideoFrameCallback(step);
    } else {
        const step = function () {
            if (token !== runToken) return;
            processFrame(performance.now());
            requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    }
}

// ============================================================
// CAMERA
// ============================================================

function arretFlux() {
    runToken += 1;
    if (stream) {
        stream.getTracks().forEach(function (t) { t.stop(); });
        stream = null;
    }
    video.srcObject = null;
}

function messageErreurCamera(nom) {
    if (nom === "NotAllowedError" || nom === "PermissionDeniedError") {
        return "Caméra refusée : autorise-la dans les paramètres du navigateur (icône 🔒 ou réglage caméra de l'appareil), puis recharge.";
    }
    if (nom === "NotFoundError" || nom === "DevicesNotFoundError") {
        return "Aucune caméra détectée sur cet appareil.";
    }
    if (nom === "NotReadableError" || nom === "TrackStartError") {
        return "Caméra déjà utilisée par une autre application. Ferme-la puis réessaie.";
    }
    return nom;
}

async function demarrerCamera(facing) {
    derniereFacing = facing;
    arretFlux();
    clearError();

    if (!window.isSecureContext) {
        showError("Caméra impossible sur HTTP. Sur le téléphone, utilise l'URL HTTPS (Vercel) — http://ip n'est pas autorisé. Sur ton PC, http://localhost fonctionne.");
        setStatut("HTTPS requis", false);
        return;
    }

    const contraintes = {
        video: {
            facingMode: facing,
            width: { ideal: 1280, max: 1280 },
            height: { ideal: 720, max: 720 },
        },
        audio: false,
    };

    try {
        stream = await navigator.mediaDevices.getUserMedia(contraintes);
    } catch (err) {
        console.warn("facingMode indisponible, repli :", err);
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                video: { width: { ideal: 1280, max: 1280 }, height: { ideal: 720, max: 720 } },
                audio: false,
            });
        } catch (err2) {
            showError("Accès caméra : " + messageErreurCamera(err2.name));
            setStatut("Caméra refusée", false);
            return;
        }
    }

    video.srcObject = stream;
    video.play();

    setStatut(facing === "user" ? "Caméra avant" : "Caméra arrière", true);

    video.addEventListener("loadeddata", function uneFois() {
        video.removeEventListener("loadeddata", uneFois);
        startLoop();
    });
}

// ============================================================
// EVENEMENTS UI
// ============================================================

document.getElementById("cam-toggle").addEventListener("click", function () {
    const suivante = derniereFacing === "user" ? "environment" : "user";
    demarrerCamera(suivante);
});

document.getElementById("reset-btn").addEventListener("click", resetCompteur);

// ============================================================
// DEMARRAGE
// ============================================================

buildCards();
document.getElementById("exo-title").textContent = detecteur.nom;
updateStatsUI(detecteur.stats());

demarrerWorker();

setStatut("Démarrage caméra…", true);

if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    showError("Caméra non disponible sur ce navigateur.");
    setStatut("Caméra absente", false);
} else {
    demarrerCamera("user");
}