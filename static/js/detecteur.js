import { EXERCICES } from "./exercices.js";

function calculerAngle(a, b, c) {
    const ba = { x: a.x - b.x, y: a.y - b.y };
    const bc = { x: c.x - b.x, y: c.y - b.y };

    const normeBA = Math.hypot(ba.x, ba.y);
    const normeBC = Math.hypot(bc.x, bc.y);

    if (normeBA === 0 || normeBC === 0) {
        return 0.0;
    }

    let cosAngle = (ba.x * bc.x + ba.y * bc.y) / (normeBA * normeBC);
    cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

    return Math.acos(cosAngle) * 180 / Math.PI;
}

function mediane(valeurs) {
    const tri = valeurs.slice().sort((x, y) => x - y);
    const n = tri.length;
    if (n % 2 === 1) {
        return tri[(n - 1) / 2];
    }
    return (tri[n / 2 - 1] + tri[n / 2]) / 2;
}

const ARTICULATIONS = {
    coude: [
        [12, 14, 16],
        [11, 13, 15],
    ],
    genou: [
        [24, 26, 28],
        [23, 25, 27],
    ],
};

export class DetecteurExercice {
    static FRAMES_VALIDATION = 3;
    static VISIBILITY_MIN = 0.5;

    constructor(typeExo) {
        if (!(typeExo in EXERCICES)) {
            throw new Error("Exercice inconnu : " + typeExo);
        }

        this.typeExo = typeExo;
        this.config = EXERCICES[typeExo];
        this.nom = this.config.nom;
        this.temps = !!this.config.temps;
        this.pause = false;

        this.reset();
    }

    reset() {
        this.compteur = 0;
        this.stage = this.config.stage_initial;
        this.nbFramesFlechi = 0;
        this.nbFramesEtendu = 0;
        this.angleCoude = null;
        this.positionValide = false;
    }

    analyser(pose) {
        this.angleCoude = null;

        if (!pose) {
            this.positionValide = false;
            return this.stats();
        }

        const c = this.config;
        const V = DetecteurExercice.VISIBILITY_MIN;

        const voies = ARTICULATIONS[c.articulation] || ARTICULATIONS.coude;

        const angles = [];
        for (const voie of voies) {
            const a = pose[voie[0]], b = pose[voie[1]], d = pose[voie[2]];
            if (a && b && d && a.visibility > V && b.visibility > V && d.visibility > V) {
                angles.push(calculerAngle(a, b, d));
            }
        }

        const angle = angles.length === 0 ? null : mediane(angles);
        this.angleCoude = angle;

        if (this.temps) {
            const epaules = (pose[11] && pose[12]);
            const hanches = (pose[23] && pose[24]);
            this.positionValide = Boolean(
                epaules && hanches &&
                pose[11].visibility > V && pose[12].visibility > V &&
                pose[23].visibility > V && pose[24].visibility > V
            );
            this.stage = this.positionValide ? "haut" : "bas";
            return this.stats();
        }

        if (angle !== null) {
            if (angle < c.angle_flechi) {
                this.nbFramesFlechi += 1;
                this.nbFramesEtendu = 0;

                if (this.nbFramesFlechi >= DetecteurExercice.FRAMES_VALIDATION) {
                    this._transition(c.haut_quand_flechi ? "haut" : "bas");
                }
            } else if (angle > c.angle_etendu) {
                this.nbFramesEtendu += 1;
                this.nbFramesFlechi = 0;

                if (this.nbFramesEtendu >= DetecteurExercice.FRAMES_VALIDATION) {
                    this._transition(c.haut_quand_flechi ? "bas" : "haut");
                }
            } else {
                this.nbFramesFlechi = 0;
                this.nbFramesEtendu = 0;
            }
        }

        return this.stats();
    }

    _transition(nouveauStage) {
        if (!this.pause && nouveauStage === "haut" && this.stage === "bas") {
            this.compteur += 1;
        }
        this.stage = nouveauStage;
    }

    stats() {
        return {
            typeExo: this.typeExo,
            nom: this.nom,
            compteur: this.compteur,
            stage: this.stage,
            angleCoude: this.angleCoude,
            temps: this.temps,
            positionValide: this.positionValide,
        };
    }
}