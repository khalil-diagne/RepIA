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

        this.reset();
    }

    reset() {
        this.compteur = 0;
        this.stage = this.config.stage_initial;
        this.nbFramesFlechi = 0;
        this.nbFramesEtendu = 0;
        this.angleCoude = null;
    }

    analyser(pose) {
        this.angleCoude = null;

        if (!pose) {
            return this.stats();
        }

        const c = this.config;
        const V = DetecteurExercice.VISIBILITY_MIN;

        const epD = pose[12], coudeD = pose[14], poignetD = pose[16];
        const epG = pose[11], coudeG = pose[13], poignetG = pose[15];

        const brasDVisible = (
            epD.visibility > V && coudeD.visibility > V && poignetD.visibility > V
        );
        const brasGVisible = (
            epG.visibility > V && coudeG.visibility > V && poignetG.visibility > V
        );

        let angleD = null;
        let angleG = null;

        if (brasDVisible) {
            angleD = calculerAngle(epD, coudeD, poignetD);
        }
        if (brasGVisible) {
            angleG = calculerAngle(epG, coudeG, poignetG);
        }

        const angles = [];
        if (angleD !== null) angles.push(angleD);
        if (angleG !== null) angles.push(angleG);

        const angle = angles.length === 0 ? null : mediane(angles);
        this.angleCoude = angle;

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
        if (nouveauStage === "haut" && this.stage === "bas") {
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
        };
    }
}