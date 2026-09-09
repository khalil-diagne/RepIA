import numpy as np
import cv2

from utils import creer_landmarker, creer_image_mp, calculer_angle, point_px


# ============================================================
# CONFIGURATION DES EXERCICES
# ============================================================
# angle_flechi   : seuil "coude fléchi" (angle en-dessous)
# angle_etendu   : seuil "bras étendu"  (angle au-dessus)
# haut_quand_flechi : True si la position "haut" est bras fléchis
#                     (tractions), False si c'est bras tendus (pompes/dips)

EXERCICES = {
    "pompes": {
        "nom": "Pompes",
        "stage_initial": "haut",
        "angle_flechi": 95,
        "angle_etendu": 155,
        "haut_quand_flechi": False,
        "menton": False,
    },
    "dips": {
        "nom": "Dips",
        "stage_initial": "haut",
        "angle_flechi": 95,
        "angle_etendu": 155,
        "haut_quand_flechi": False,
        "menton": False,
    },
    "tractions": {
        "nom": "Tractions",
        "stage_initial": "bas",
        "angle_flechi": 120,
        "angle_etendu": 160,
        "haut_quand_flechi": True,
        "menton": True,
    },
}


# ============================================================
# LOGIQUE DE DETECTION / COMPTAGE (sans affichage)
# ============================================================

class DetecteurExercice:
    """Encapsule la détection et le comptage d'un exercice donné."""

    FRAMES_VALIDATION = 3
    VISIBILITY_MIN = 0.5

    def __init__(self, type_exo):
        if type_exo not in EXERCICES:
            raise ValueError(f"Exercice inconnu : {type_exo}")

        self.type_exo = type_exo
        self.config = EXERCICES[type_exo]
        self.nom = self.config["nom"]

        self.landmarker = creer_landmarker()

        self.reset()

    # --------------------------------------------------------
    # REINITIALISATION
    # --------------------------------------------------------

    def reset(self):
        self.frame_timestamp_ms = 0

        self.compteur = 0
        self.stage = self.config["stage_initial"]

        self.nb_frames_flechi = 0
        self.nb_frames_etendu = 0

        self.angle_coude = None

    # --------------------------------------------------------
    # TRAITEMENT D'UNE FRAME
    # --------------------------------------------------------

    def process(self, frame, miroir=True):
        """Analyse une frame, dessine les landmarks et met à jour le compteur."""
        if miroir:
            frame = cv2.flip(frame, 1)

        mp_image = creer_image_mp(frame)

        resultats = self.landmarker.detect_for_video(
            mp_image,
            self.frame_timestamp_ms
        )

        self.frame_timestamp_ms += 33

        self.angle_coude = None

        if resultats.pose_landmarks:

            landmarks = resultats.pose_landmarks[0]

            # --------------------------------------------------
            # LANDMARKS
            # --------------------------------------------------

            menton = landmarks[0] if self.config["menton"] else None

            epaule_d = landmarks[12]
            coude_d = landmarks[14]
            poignet_d = landmarks[16]

            epaule_g = landmarks[11]
            coude_g = landmarks[13]
            poignet_g = landmarks[15]

            hanche_d = landmarks[24]
            hanche_g = landmarks[23]

            cheville_d = landmarks[28]
            cheville_g = landmarks[27]

            # --------------------------------------------------
            # VISIBILITE
            # --------------------------------------------------

            bras_d_visible = (
                epaule_d.visibility > self.VISIBILITY_MIN
                and coude_d.visibility > self.VISIBILITY_MIN
                and poignet_d.visibility > self.VISIBILITY_MIN
            )

            bras_g_visible = (
                epaule_g.visibility > self.VISIBILITY_MIN
                and coude_g.visibility > self.VISIBILITY_MIN
                and poignet_g.visibility > self.VISIBILITY_MIN
            )

            angle_coude_d = None
            angle_coude_g = None

            if bras_d_visible:
                angle_coude_d = calculer_angle(
                    [epaule_d.x, epaule_d.y],
                    [coude_d.x, coude_d.y],
                    [poignet_d.x, poignet_d.y]
                )

            if bras_g_visible:
                angle_coude_g = calculer_angle(
                    [epaule_g.x, epaule_g.y],
                    [coude_g.x, coude_g.y],
                    [poignet_g.x, poignet_g.y]
                )

            angles = []

            if angle_coude_d is not None:
                angles.append(angle_coude_d)

            if angle_coude_g is not None:
                angles.append(angle_coude_g)

            if len(angles) == 0:
                angle_coude = None
            else:
                angle_coude = float(np.median(angles))

            # --------------------------------------------------
            # COORDONNEES PIXELS
            # --------------------------------------------------

            pts = {
                "ep_d": point_px(epaule_d, frame),
                "co_d": point_px(coude_d, frame),
                "po_d": point_px(poignet_d, frame),

                "ep_g": point_px(epaule_g, frame),
                "co_g": point_px(coude_g, frame),
                "po_g": point_px(poignet_g, frame),

                "hanche_d": point_px(hanche_d, frame),
                "hanche_g": point_px(hanche_g, frame),

                "cheville_d": point_px(cheville_d, frame),
                "cheville_g": point_px(cheville_g, frame),
            }

            if menton is not None:
                pts["menton"] = point_px(menton, frame)

            # --------------------------------------------------
            # DESSIN DES BRAS
            # --------------------------------------------------

            couleur_ligne = (255, 0, 0)

            cv2.line(frame, pts["ep_d"], pts["co_d"], couleur_ligne, 3)
            cv2.line(frame, pts["co_d"], pts["po_d"], couleur_ligne, 3)
            cv2.line(frame, pts["ep_g"], pts["co_g"], couleur_ligne, 3)
            cv2.line(frame, pts["co_g"], pts["po_g"], couleur_ligne, 3)

            # --------------------------------------------------
            # DESSIN DU CORPS
            # --------------------------------------------------

            cv2.line(frame, pts["ep_d"], pts["hanche_d"], couleur_ligne, 2)
            cv2.line(frame, pts["ep_g"], pts["hanche_g"], couleur_ligne, 2)
            cv2.line(frame, pts["hanche_d"], pts["cheville_d"], couleur_ligne, 2)
            cv2.line(frame, pts["hanche_g"], pts["cheville_g"], couleur_ligne, 2)

            # --------------------------------------------------
            # POINTS
            # --------------------------------------------------

            for cle, p in pts.items():
                if cle == "menton":
                    cv2.circle(frame, p, 8, (0, 255, 255), -1)
                else:
                    cv2.circle(frame, p, 6, (0, 255, 0), -1)

            # --------------------------------------------------
            # LOGIQUE DE COMPTAGE
            # --------------------------------------------------

            if angle_coude is not None:

                if angle_coude < self.config["angle_flechi"]:

                    self.nb_frames_flechi += 1
                    self.nb_frames_etendu = 0

                    if self.nb_frames_flechi >= self.FRAMES_VALIDATION:
                        nouveau_stage = (
                            "haut" if self.config["haut_quand_flechi"] else "bas"
                        )
                        self._transition(nouveau_stage)

                elif angle_coude > self.config["angle_etendu"]:

                    self.nb_frames_etendu += 1
                    self.nb_frames_flechi = 0

                    if self.nb_frames_etendu >= self.FRAMES_VALIDATION:
                        nouveau_stage = (
                            "bas" if self.config["haut_quand_flechi"] else "haut"
                        )
                        self._transition(nouveau_stage)

                else:

                    self.nb_frames_flechi = 0
                    self.nb_frames_etendu = 0

                # -----------------------------------------
                # AFFICHAGE ANGLE
                # -----------------------------------------

                self.angle_coude = angle_coude

                cv2.putText(
                    frame,
                    f"Angle coude : {angle_coude:.1f} deg",
                    (20, 130),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.8,
                    (255, 255, 255),
                    2
                )

            else:

                cv2.putText(
                    frame,
                    "Bras non detecte",
                    (20, 130),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.8,
                    (0, 0, 255),
                    2
                )

        # ====================================================
        # INTERFACE (dessinée directement sur la frame)
        # ====================================================

        cv2.rectangle(frame, (0, 0), (360, 100), (0, 0, 0), -1)

        cv2.putText(
            frame,
            f"{self.nom} : {self.compteur}",
            (15, 40),
            cv2.FONT_HERSHEY_SIMPLEX,
            1.1,
            (255, 255, 255),
            2
        )

        cv2.putText(
            frame,
            f"Position : {self.stage}",
            (15, 75),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.8,
            (255, 255, 255),
            2
        )

        return frame

    # --------------------------------------------------------
    # TRANSITION DE STAGE (avec comptage)
    # --------------------------------------------------------

    def _transition(self, nouveau_stage):
        if nouveau_stage == "haut" and self.stage == "bas":
            self.compteur += 1
            print(f"{self.nom} validee : {self.compteur}")

        self.stage = nouveau_stage

    # --------------------------------------------------------
    # ETAT COURANT (pour /stats)
    # --------------------------------------------------------

    def stats(self):
        return {
            "type_exo": self.type_exo,
            "nom": self.nom,
            "compteur": self.compteur,
            "stage": self.stage,
            "angle_coude": self.angle_coude,
        }

    # --------------------------------------------------------
    # NETTOYAGE
    # --------------------------------------------------------

    def close(self):
        self.landmarker.close()