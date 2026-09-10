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

const ALPHA_LISSAGE = 0.55;
const PERIODE_ALERTE_AUDIO = 30000;
const ALERTE_AUDIO_APRES_MS = 3000;

const AUDIO_ALERTES = {
    horsCadre: "hors_cadre.wav",
    partiel: "partiel.wav",
    gainage: "gainage.wav",
    serieFinie: "serie_finie.wav",
    seanceFinie: "seance_finie.wav",
    reposFini: "repos_fini.wav",
    squadDebut: "squaddebut.wav"
};

const HIST_KEY = "repia_historique";
const PREFS_KEY = "repia_prefs";

const OBJECTIFS = [
    { id: "libre", lib: "Libre", series: 0, reps: 0 },
    { id: "3x12", lib: "3 x 12", series: 3, reps: 12 },
    { id: "4x10", lib: "4 x 10", series: 4, reps: 10 },
    { id: "3x8", lib: "3 x 8", series: 3, reps: 8 },
    { id: "2x15", lib: "2 x 15", series: 2, reps: 15 },
];

const REPOS_PRESETS = [0, 30, 60, 90];

const ICONE_DEFAUT =
    "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='m6.5 6.5 11 11'/><path d='m21 21-1-1'/><path d='m3 3 1 1'/><path d='m18 22 4-4'/><path d='m2 6 4-4'/><path d='m3 10 7-7'/><path d='m14 21 7-7'/></svg>";

function $(id) {
    return document.getElementById(id);
}

function lireStock(cle) {
    try { return JSON.parse(localStorage.getItem(cle)); } catch (e) { return null; }
}
function ecrireStock(cle, valeur) {
    try { localStorage.setItem(cle, JSON.stringify(valeur)); } catch (e) {}
}

const video = $(`video`);
const canvas = $(`canvas`);
const ctx = canvas.getContext("2d");

let detecteur = new DetecteurExercice("pompes");
let pose = null;
let poseLisse = null;

let stream = null;
let runToken = 0;
let derniereFacing = "user";
let cameraActif = false;

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

let fpsMax = 0;
let dernierTraitementAt = 0;

let enRepos = false;
let reposRestantMs = 0;
let reposFinAt = 0;
let reposTimer = null;

let objectifId = "libre";
let seriesTotal = 0;
let repsCible = 0;
let serieEnCours = 1;
let totalRepsSeance = 0;
let dernierCompteurVu = 0;
let premiereSerieJouee = false;

let tempsPlankMs = 0;
let dernierePoseHeldAt = 0;

let dureeSeanceMs = 0;
let sessionDebAt = 0;
let sessionTimerId = null;

let sonActif = true;
let audioCtx = null;
let dernierAlerteSonAt = 0;
let alerteCle = null;
let alerteDepuis = 0;

let focusActif = false;

// ============================================================
// PREFS PERSISTEES
// ============================================================

const PREFS = Object.assign(
    { objectif: "libre", repos: 30, fps: "auto", son: true },
    lireStock(PREFS_KEY) || {}
);

function sauvegarderPrefs() {
    ecrireStock(PREFS_KEY, {
        objectif: objectifId,
        repos: PREFS.repos,
        fps: PREFS.fps,
        son: sonActif,
    });
}

// ============================================================
// UI COURANTE (état / bannière)
// ============================================================

function showError(msg) {
    $(`cam-banner`).textContent = msg;
    $(`cam-banner`).classList.add("visible");
}

function clearError() {
    $(`cam-banner`).classList.remove("visible");
}

function setStatut(texte, enLigne) {
    $(`status-text`).textContent = texte;
    $(`dot`).className = "dot " + (enLigne ? "online" : "offline");
}

function montrerToast(msg, duree) {
    const t = $(`toast`);
    t.textContent = msg;
    t.classList.add("visible");
    clearTimeout(t._timer);
    t._timer = setTimeout(function () {
        t.classList.remove("visible");
    }, duree || 2600);
}

function animerCompteur() {
    const el = $(`compteur`);
    el.classList.remove("tick");
    void el.offsetWidth;
    el.classList.add("tick");
}

// ============================================================
// AUDIO
// ============================================================

function garantirAudio() {
    if (!audioCtx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (AC) audioCtx = new AC();
    }
    if (audioCtx && audioCtx.state === "suspended") {
        audioCtx.resume();
    }
    return audioCtx;
}

function bip(freq, duree, gain, decalage) {
    if (!sonActif) return;
    const ctx = garantirAudio();
    if (!ctx) return;
    const q = ctx.currentTime + (decalage || 0);
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.type = "sine";
    o.frequency.value = freq;
    g.gain.setValueAtTime(0.0001, q);
    g.gain.exponentialRampToValueAtTime(gain, q + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, q + duree);
    o.connect(g);
    g.connect(ctx.destination);
    o.start(q);
    o.stop(q + duree + 0.05);
}

function bipRep() {
    bip(660, 0.07, 0.12, 0);
}
function bipSerie() {
    bip(880, 0.12, 0.14, 0);
    bip(1175, 0.16, 0.14, 0.15);
}
function bipFinRepos() {
    bip(660, 0.1, 0.12, 0);
    bip(880, 0.1, 0.13, 0.15);
    bip(1175, 0.18, 0.13, 0.3);
}
function bipFinSeance() {
    bip(660, 0.12, 0.13, 0);
    bip(880, 0.12, 0.14, 0.14);
    bip(1175, 0.12, 0.14, 0.28);
    bip(1568, 0.28, 0.15, 0.42);
}

// ============================================================
// AUDIOS WOLOF (fichiers enregistrés, aucun TTS)
// ============================================================

const audioCache = {};

function prechargerAudios() {
    Object.keys(AUDIO_ALERTES).forEach(function (k) {
        const a = new Audio();
        a.src = "/static/audio/wo/" + AUDIO_ALERTES[k];
        a.preload = "auto";
        audioCache[k] = a;
    });
}

function arreterAudio() {
    Object.keys(audioCache).forEach(function (k) {
        const a = audioCache[k];
        if (!a) return;
        try {
            a.pause();
            a.currentTime = 0;
        } catch (e) {}
    });
}

function jouerAlerte(cle) {
    if (!sonActif) return;
    const a = audioCache[cle];
    if (!a) return;
    arreterAudio();
    try {
        a.currentTime = 0;
        const promesse = a.play();
        if (promesse && promesse.catch) promesse.catch(function () {});
    } catch (e) {}
}

document.addEventListener("pointerdown", function porteAudio() {
    garantirAudio();
}, { once: true });

// ============================================================
// LISSAGE DES KEYPOINTS (filtre passe-bas / EMA)
// ============================================================

function lisserPose(brut) {
    if (!brut) {
        poseLisse = null;
        return null;
    }

    if (!poseLisse || poseLisse.length !== brut.length) {
        poseLisse = brut.map(function (p) {
            return { x: p.x, y: p.y, z: p.z, visibility: p.visibility };
        });
        return poseLisse;
    }

    for (let i = 0; i < brut.length; i += 1) {
        const p = brut[i];
        const s = poseLisse[i];
        if (!p) continue;
        s.x = s.x + ALPHA_LISSAGE * (p.x - s.x);
        s.y = s.y + ALPHA_LISSAGE * (p.y - s.y);
        s.z = s.z + ALPHA_LISSAGE * (p.z - s.z);
        if (p.visibility != null) {
            s.visibility = s.visibility + 0.3 * (p.visibility - s.visibility);
        }
    }

    return poseLisse;
}

// ============================================================
// CARTES D'EXERCICES (sélecteur segmenté)
// ============================================================

function construireSelecteur(conteneur) {
    const box = $(conteneur);
    if (!box) return;
    box.innerHTML = "";
    Object.keys(EXERCICES).forEach(function (id) {
        const cfg = EXERCICES[id];
        const meta = EXO_META[id] || { icon: ICONE_DEFAUT, desc: "" };
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
    if (id === detecteur.typeExo) {
        fermerFeuille();
        return;
    }

    detecteur = new DetecteurExercice(id);
    $(`exo-title`).textContent = detecteur.nom;

    document.querySelectorAll(".exo").forEach(function (el) {
        el.classList.toggle("active", el.dataset.id === id);
    });

    fermerFeuille();

    pose = null;
    poseLisse = null;
    dernierCompteurVu = 0;
    tempsPlankMs = 0;
    dernierePoseHeldAt = 0;
    serieEnCours = 1;
    totalRepsSeance = 0;
    arreterRepos();

    majProgression();
    afficherStats(detecteur.stats());

    if (id === "squats" || id === "plank") {
        jouerAlerte("squadDebut");
    }
}

function resetCompteur() {
    const reps = detecteur.temps ? Math.round(tempsPlankMs / 1000) : detecteur.compteur;
    if (reps > 0) {
        enregistrerHistorique(reps, 1, dureeSeanceMs);
        rendreHistorique();
    }

    detecteur.pause = false;
    detecteur.reset();
    pose = null;
    poseLisse = null;
    dernierCompteurVu = 0;
    tempsPlankMs = 0;
    dernierePoseHeldAt = 0;
    serieEnCours = 1;
    totalRepsSeance = 0;
    arreterRepos();

    majProgression();
    afficherStats(detecteur.stats());
}

// ============================================================
// STATS (UI)
// ============================================================

function formaterDuree(sec) {
    const s = Math.max(0, Math.floor(sec || 0));
    const mn = Math.floor(s / 60);
    const ss = s % 60;
    function p2(n) { return (n < 10 ? "0" : "") + n; }
    return p2(mn) + ":" + p2(ss);
}

function afficherStats(st) {
    $(`compteur`).textContent = st.temps ? formaterDuree(st.compteur) : String(st.compteur);
    $(`stage`).textContent = st.temps ? (st.positionValide ? "Tenue" : "—") : (st.stage.charAt(0).toUpperCase() + st.stage.slice(1));
    $(`angle`).textContent = st.angleCoude != null ? st.angleCoude.toFixed(1) + "°" : "—";
    majProgression();
}

function majProgression() {
    $(`serie-progress`).textContent =
        repsCible > 0 ? "Série " + Math.min(serieEnCours, seriesTotal) + "/" + seriesTotal : "Libre";
    $(`rest-timer`).textContent = enRepos ? "Repos " + Math.ceil(reposRestantMs / 1000) + "s" : "";
    $(`rest-timer`).classList.toggle("hidden", !enRepos);
}

// ============================================================
// CHRONO SÉANCE
// ============================================================

function demarrerChrono() {
    sessionDebAt = performance.now();
    if (!sessionTimerId) {
        sessionTimerId = setInterval(function () {
            dureeSeanceMs = performance.now() - sessionDebAt;
            $(`session-timer`).textContent = formaterDuree(dureeSeanceMs / 1000);
        }, 1000);
    }
}

function arreterChrono() {
    if (sessionTimerId) {
        clearInterval(sessionTimerId);
        sessionTimerId = null;
    }
}

// ============================================================
// DÉTECTION / COMPTAGE / OBJECTIFS / REPOS
// ============================================================

function onPose(raw) {
    pose = lisserPose(raw);
    const st = detecteur.analyser(pose);

    if (detecteur.temps) {
        gererTempsPlank(st.positionValide);
    }

    if (!enRepos) {
        if (detecteur.temps) {
            if (repsCible > 0 && detecteur.compteur >= repsCible) {
                verifierSerie(detecteur.compteur);
            }
        } else {
            const apres = detecteur.compteur;
            if (apres > dernierCompteurVu) {
                dernierCompteurVu = apres;
                bipRep();
                animerCompteur();
                if (repsCible > 0 && apres >= repsCible) {
                    verifierSerie(apres);
                }
            }
        }
    }

    majAlertes(st);
    afficherStats(detecteur.stats());
}

function gererTempsPlank(positionValide) {
    const now = performance.now();
    if (positionValide && !enRepos) {
        if (dernierePoseHeldAt) {
            tempsPlankMs += now - dernierePoseHeldAt;
        }
        dernierePoseHeldAt = now;
    } else {
        dernierePoseHeldAt = null;
    }
    detecteur.compteur = Math.floor(tempsPlankMs / 1000);
}

function verifierSerie(v) {
    if (repsCible <= 0) return;

    detecteur.pause = true;
    totalRepsSeance += v;

    if (serieEnCours >= seriesTotal) {
        terminerSeance();
        return;
    }

    bipSerie();
    jouerAlerte("serieFinie");
    montrerToast("Série " + serieEnCours + "/" + seriesTotal + " terminée");

    serieEnCours += 1;
    majProgression();

    if (PREFS.repos > 0) {
        demarrerRepos();
    } else {
        relancerSerie();
    }
}

function relancerSerie() {
    if (detecteur.typeExo === "squats") {
        jouerAlerte("squadDebut");
    }
    detecteur.pause = false;
    detecteur.reset();
    dernierCompteurVu = 0;
    tempsPlankMs = 0;
    dernierePoseHeldAt = 0;
    afficherStats(detecteur.stats());
}

function terminerSeance() {
    const reps = totalRepsSeance;
    const series = seriesTotal;
    const duree = dureeSeanceMs;

    enregistrerHistorique(reps, series, duree);
    rendreHistorique();

    bipFinSeance();
    jouerAlerte("seanceFinie");
    montrerToast("Séance terminée — bravo", 3600);

    detecteur.pause = false;
    detecteur.reset();
    dernierCompteurVu = 0;
    tempsPlankMs = 0;
    dernierePoseHeldAt = 0;
    enRepos = false;
    serieEnCours = 1;
    totalRepsSeance = 0;
    arreterRepos();

    majProgression();
    afficherStats(detecteur.stats());

    afficherFelicitation(reps, series, duree);
}

function afficherFelicitation(reps, series, dureeMs) {
    $(`felic-overlay`).classList.add("open");
    $(`felic-reps`).textContent = reps + " reps";
    $(`felic-series`).textContent = series + (series > 1 ? " séries" : " série");
    $(`felic-duree`).textContent = formaterDuree(dureeMs / 1000);
    detecteur.pause = true;
}

function fermerFelicitation() {
    $(`felic-overlay`).classList.remove("open");
    detecteur.pause = false;
}

function demarrerRepos() {
    enRepos = true;
    reposRestantMs = PREFS.repos * 1000;
    reposFinAt = Date.now() + reposRestantMs;
    detecteur.pause = true;
    majRestUI();
    reposTimer = setInterval(tickRepos, 200);
}

function tickRepos() {
    reposRestantMs = Math.max(0, reposFinAt - Date.now());
    majRestUI();
    if (reposRestantMs <= 0) {
        clearInterval(reposTimer);
        reposTimer = null;
        enRepos = false;
        detecteur.pause = false;
        bipFinRepos();
        jouerAlerte("reposFini");
        majRestUI();
        relancerSerie();
    }
}

function arreterRepos() {
    if (reposTimer) {
        clearInterval(reposTimer);
        reposTimer = null;
    }
    enRepos = false;
    majRestUI();
}

function majRestUI() {
    $(`rest-timer`).textContent = enRepos ? "Repos " + Math.ceil(reposRestantMs / 1000) + "s" : "";
    $(`rest-timer`).classList.toggle("hidden", !enRepos);
    $(`rest-overlay`).classList.toggle("visible", enRepos);
    if (enRepos) {
        $(`rest-num`).textContent = Math.ceil(reposRestantMs / 1000);
    }
    majProgression();
}

// ============================================================
// ALERTES POSTURE (visuel + audio)
// ============================================================

function majAlertes(st) {
    const badge = $(`alert-badge`);
    let cle = null;
    let message = null;

    if (cameraActif) {
        if (!pose) {
            cle = "horsCadre";
            message = "Positionne-toi dans le cadre";
        } else if (st.temps) {
            if (!st.positionValide) {
                cle = "gainage";
                message = "Adopte la position de gainage";
            }
        } else if (st.angleCoude == null) {
            cle = "partiel";
            message = "Corps partiellement visible";
        }
    }

    const maintenant = Date.now();

    if (cle) {
        if (alerteCle !== cle) {
            alerteCle = cle;
            alerteDepuis = maintenant;
        }
        const persistant = maintenant - alerteDepuis >= ALERTE_AUDIO_APRES_MS;
        badge.textContent = message;
        badge.classList.toggle("visible", persistant);

        if (persistant &&
            maintenant - dernierAlerteSonAt > PERIODE_ALERTE_AUDIO) {
            dernierAlerteSonAt = maintenant;
            jouerAlerte(cle);
        }
    } else {
        alerteCle = null;
        alerteDepuis = 0;
        dernierAlerteSonAt = 0;
        badge.classList.remove("visible");
    }
}

// ============================================================
// HISTORIQUE (localStorage)
// ============================================================

function lireHistorique() {
    const h = lireStock(HIST_KEY);
    return Array.isArray(h) ? h : [];
}

function enregistrerHistorique(reps, series, dureeMs) {
    if (!reps || reps <= 0) return;
    const hist = lireHistorique();
    hist.push({
        t: Date.now(),
        exo: detecteur.nom,
        reps: reps,
        series: series,
        dureeMs: Math.round(dureeMs || 0),
    });
    ecrireStock(HIST_KEY, hist.slice(-100));
}

function rendreHistorique() {
    const hist = lireHistorique();
    const week = $(`hist-week`);
    const list = $(`hist-list`);
    const clear = $(`hist-clear`);
    list.innerHTML = "";
    if (clear) clear.classList.toggle("hidden", hist.length === 0);

    const maintenant = Date.now();
    const semaine = hist.filter(function (r) {
        return (maintenant - r.t) < 7 * 24 * 3600 * 1000;
    });
    const repsSemaine = semaine.reduce(function (acc, r) { return acc + r.reps; }, 0);

    if (week) {
        week.textContent = hist.length === 0
            ? "Aucune séance"
            : semaine.length + " séance" + (semaine.length > 1 ? "s" : "") +
              " · " + repsSemaine + " rep" + (repsSemaine > 1 ? "s" : "") + " (7 j)";
    }

    hist.slice(-10).reverse().forEach(function (r) {
        const li = document.createElement("li");
        const date = new Date(r.t);
        const texte = date.toLocaleDateString("fr-FR", {
            day: "numeric",
            month: "short",
            hour: "2-digit",
            minute: "2-digit",
        });
        const duree = r.dureeMs ? formaterDuree(r.dureeMs / 1000) : "";
        li.innerHTML =
            '<span class="h-date">' + texte + "</span>" +
            '<span class="h-exo">' + escapeHtml(r.exo) +
            (duree ? "<small>" + duree + "</small>" : "") + "</span>" +
            '<span class="h-val">' + r.reps + " rep" + (r.reps > 1 ? "s" : "") + "</span>";
        list.appendChild(li);
    });
}

function escapeHtml(texte) {
    const d = document.createElement("div");
    d.textContent = texte;
    return d.innerHTML;
}

// ============================================================
// OBJECTIFS / REPOS / FPS / SON / FOCUS (UI)
// ============================================================

function construireOptionsObjectif() {
    const box = $(`obj-options`);
    OBJECTIFS.forEach(function (o) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "ob" + (o.id === PREFS.objectif ? " active" : "");
        btn.dataset.ob = o.id;
        btn.textContent = o.lib;
        btn.addEventListener("click", function () {
            appliquerObjectif(o.id);
        });
        box.appendChild(btn);
    });
}

function appliquerObjectif(id) {
    PREFS.objectif = id;
    objectifId = id;
    sauvegarderPrefs();

    const obj = OBJECTIFS.find(function (o) { return o.id === id; }) || OBJECTIFS[0];
    seriesTotal = obj.series;
    repsCible = obj.reps;

    document.querySelectorAll(".ob").forEach(function (b) {
        b.classList.toggle("active", b.dataset.ob === id);
    });

    detecteur.pause = false;
    detecteur.reset();
    dernierCompteurVu = 0;
    tempsPlankMs = 0;
    dernierePoseHeldAt = 0;
    serieEnCours = 1;
    totalRepsSeance = 0;
    arreterRepos();

    majProgression();
    afficherStats(detecteur.stats());
}

function construireOptionsRepos() {
    const box = $(`repos-options`);
    REPOS_PRESETS.forEach(function (sec) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "rp" + (sec === PREFS.repos ? " active" : "");
        btn.dataset.repos = String(sec);
        btn.textContent = sec === 0 ? "0s" : sec + "s";
        btn.addEventListener("click", function () {
            PREFS.repos = sec;
            sauvegarderPrefs();
            document.querySelectorAll(".rp").forEach(function (b) {
                b.classList.toggle("active", b.dataset.repos === String(sec));
            });
        });
        box.appendChild(btn);
    });
}

function configurerFps() {
    const selects = document.querySelectorAll("#fps-select, #fps-select-desktop");
    selects.forEach(function (sel) {
        sel.value = PREFS.fps;
        sel.addEventListener("change", function () {
            PREFS.fps = sel.value;
            fpsMax = sel.value === "auto" ? 0 : Number(sel.value);
            sauvegarderPrefs();
            selects.forEach(function (autre) { autre.value = PREFS.fps; });
        });
    });
    fpsMax = PREFS.fps === "auto" ? 0 : Number(PREFS.fps);
}

function configurerSon() {
    const boutons = document.querySelectorAll("#sound-btn, #sound-btn-desktop");
    sonActif = PREFS.son;
    majBtnSon();
    boutons.forEach(function (btn) {
        btn.addEventListener("click", function () {
            sonActif = !sonActif;
            PREFS.son = sonActif;
            sauvegarderPrefs();
            majBtnSon();
            garantirAudio();
            if (!sonActif && audioCtx) {
                audioCtx.suspend();
            }
        });
    });
}

function majBtnSon() {
    document.querySelectorAll("#sound-btn, #sound-btn-desktop").forEach(function (b) {
        b.classList.toggle("muted", !sonActif);
    });
}

function configurerFocus() {
    $(`focus-btn`).addEventListener("click", function () {
        basculerFocus();
    });

    function gererFin() {
        if (!document.fullscreenElement && !document.webkitFullscreenElement) {
            if (focusActif) {
                focusActif = false;
                document.body.classList.remove("focus");
                majBtnFocus();
            }
        }
    }
    document.addEventListener("fullscreenchange", gererFin);
    document.addEventListener("webkitfullscreenchange", gererFin);
}

function basculerFocus() {
    focusActif = !focusActif;
    document.body.classList.toggle("focus", focusActif);
    majBtnFocus();

    try {
        const el = document.documentElement;
        if (focusActif) {
            const req = el.requestFullscreen || el.webkitRequestFullscreen;
            if (req) req.call(el);
        } else {
            const ex = document.exitFullscreen || document.webkitExitFullscreen;
            if (ex && (document.fullscreenElement || document.webkitFullscreenElement)) {
                ex.call(document);
            }
        }
    } catch (e) {}
}

function majBtnFocus() {
    $(`focus-btn`).classList.toggle("active", focusActif);
}

// ============================================================
// DESSIN (squelette sobre + guide d'alignement)
// ============================================================

function dessinerPose(pose, W, H) {
    if (!pose) return;

    const couleurLigne = "rgba(232, 203, 94, 0.9)";
    const couleurPoint = "rgba(255, 255, 255, 0.85)";
    const couleurMenton = "rgba(232, 203, 94, 0.95)";

    ctx.lineWidth = 2;
    ctx.lineCap = "round";
    ctx.strokeStyle = couleurLigne;
    ctx.beginPath();
    ctx.moveTo(pose[12].x * W, pose[12].y * H);
    ctx.lineTo(pose[14].x * W, pose[14].y * H);
    ctx.lineTo(pose[16].x * W, pose[16].y * H);
    ctx.moveTo(pose[11].x * W, pose[11].y * H);
    ctx.lineTo(pose[13].x * W, pose[13].y * H);
    ctx.lineTo(pose[15].x * W, pose[15].y * H);
    ctx.stroke();

    ctx.lineWidth = 1.5;
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
        ctx.arc(pose[i].x * W, pose[i].y * H, 2.5, 0, 2 * Math.PI);
        ctx.fill();
    }

    if (detecteur.config.menton && pose[0]) {
        ctx.fillStyle = couleurMenton;
        ctx.beginPath();
        ctx.arc(pose[0].x * W, pose[0].y * H, 3.5, 0, 2 * Math.PI);
        ctx.fill();
    }
}

function dessinerGuide(W, H) {
    const cx = W * 0.5;
    const lga = W * 0.13;
    const tete = H * 0.20;
    const epaulesY = H * 0.35;
    const hanchesY = H * 0.52;
    const mainsY = H * 0.68;
    const chevillesY = H * 0.88;

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.strokeStyle = "rgba(255, 255, 255, 0.2)";
    ctx.lineWidth = Math.max(2.5, W * 0.005);
    ctx.lineCap = "round";

    ctx.beginPath();
    ctx.arc(cx, tete, H * 0.05, 0, 2 * Math.PI);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(cx - lga, epaulesY);
    ctx.lineTo(cx + lga, epaulesY);
    ctx.lineTo(cx + lga, hanchesY);
    ctx.lineTo(cx - lga, hanchesY);
    ctx.closePath();
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(cx - lga, epaulesY);
    ctx.lineTo(cx - lga * 1.5, mainsY);
    ctx.moveTo(cx + lga, epaulesY);
    ctx.lineTo(cx + lga * 1.5, mainsY);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(cx - lga, hanchesY);
    ctx.lineTo(cx - lga * 1.25, chevillesY);
    ctx.moveTo(cx + lga, hanchesY);
    ctx.lineTo(cx + lga * 1.25, chevillesY);
    ctx.stroke();
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
    if (!pose) {
        dessinerGuide(W, H);
    }
}

// ============================================================
// WORKER (chemin privilégié) + repli thread principal
// ============================================================

function demarrerWorker() {
    try {
        worker = new Worker("/static/js/worker.js", { type: "module" });
    } catch (err) {
        workerEchec = true;
        activerRepliThread();
        return;
    }

    worker.addEventListener("message", function (ev) {
        const msg = ev.data;

        if (msg.type === "ready") {
            workerPret = true;
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
                $(`perf`).textContent =
                    tailleCap + "px · " + (diviseurFrames > 1 ? "1/" + diviseurFrames + " · " : "") +
                    Math.round(fps) + " fps";
            }
            dernierResultAt = maintenant;

            const raw = msg.landmarks ? msg.landmarks.map(function (l) {
                return { x: l[0], y: l[1], z: l[2], visibility: l[3] };
            }) : null;

            onPose(raw);
        }
    });

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

function pretPourDetection(now) {
    if (!fpsMax) return true;
    if (now - dernierTraitementAt < 1000 / fpsMax) {
        return false;
    }
    dernierTraitementAt = now;
    return true;
}

function envoyerFrameWorker(now) {
    if (workerOccupe || !pretPourDetection(now)) {
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

    if (!landmarker || !pretPourDetection(now)) {
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
        envoyerFrameWorker(now);
    } else if (workerEchec) {
        traiterFrameThreadPrincipal(now);
    }

    dessiner(W, H);
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
    cameraActif = false;
    arreterChrono();
    majAlertes(detecteur.stats());
}

function messageErreurCamera(nom) {
    if (nom === "NotAllowedError" || nom === "PermissionDeniedError") {
        return "Caméra refusée : autorise-la dans les réglages du navigateur (cadenas dans la barre d'adresse ou réglage caméra de l'appareil), puis recharge.";
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

    cameraActif = true;

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
            cameraActif = false;
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
        demarrerChrono();
        if (!premiereSerieJouee && detecteur.typeExo === "squats") {
            premiereSerieJouee = true;
            jouerAlerte("squadDebut");
        }
    });
}

// ============================================================
// FEUILLE D'EXERCICES (mobile)
// ============================================================

function ouvrirFeuille() {
    $(`exo-sheet`).classList.add("open");
    $(`exo-backdrop`).classList.add("visible");
}

function fermerFeuille() {
    $(`exo-sheet`).classList.remove("open");
    $(`exo-backdrop`).classList.remove("visible");
}

// ============================================================
// EVENEMENTS UI
// ============================================================

document.getElementById("cam-toggle").addEventListener("click", function () {
    const suivante = derniereFacing === "user" ? "environment" : "user";
    demarrerCamera(suivante);
});

document.getElementById("reset-btn").addEventListener("click", resetCompteur);

document.getElementById("bb-exercices").addEventListener("click", ouvrirFeuille);
document.getElementById("exo-backdrop").addEventListener("click", fermerFeuille);
document.getElementById("bb-reset").addEventListener("click", resetCompteur);
document.getElementById("bb-focus").addEventListener("click", basculerFocus);
document.getElementById("exit-focus").addEventListener("click", basculerFocus);

document.getElementById("felic-recommencer").addEventListener("click", function () {
    fermerFelicitation();
    resetCompteur();
});
document.getElementById("felic-fermer").addEventListener("click", fermerFelicitation);

document.getElementById("hist-toggle").addEventListener("click", function () {
    const corps = document.getElementById("hist-body");
    const ouvert = corps.classList.toggle("open");
    this.classList.toggle("open", ouvert);
    this.setAttribute("aria-expanded", ouvert ? "true" : "false");
    if (ouvert) {
        rendreHistorique();
    }
});

document.getElementById("hist-clear").addEventListener("click", function () {
    try {
        localStorage.removeItem(HIST_KEY);
    } catch (e) {}
    rendreHistorique();
});

// ============================================================
// DEMARRAGE
// ============================================================

construireSelecteur("exercises");
construireSelecteur("exo-focus");
construireSelecteur("sheet-selector");
construireOptionsObjectif();
construireOptionsRepos();
configurerFps();
configurerSon();
configurerFocus();
prechargerAudios();
rendreHistorique();

document.getElementById("exo-title").textContent = detecteur.nom;

appliquerObjectif(PREFS.objectif);
afficherStats(detecteur.stats());

demarrerWorker();

setStatut("Démarrage caméra…", true);

if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    showError("Caméra non disponible sur ce navigateur.");
    setStatut("Caméra absente", false);
} else {
    demarrerCamera("user");
}