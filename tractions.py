import cv2
import numpy as np

from utils import creer_landmarker, creer_image_mp, calculer_angle, point_px


# ============================================================
# 1. INITIALISATION
# ============================================================

landmarker = creer_landmarker()

cap = cv2.VideoCapture(0)

if not cap.isOpened():
    print("Erreur : impossible d'ouvrir la caméra.")
    landmarker.close()
    raise SystemExit


# ============================================================
# 2. VARIABLES
# ============================================================

frame_timestamp_ms = 0

compteur = 0

# Etat initial
stage = "bas"

# Pour éviter les changements trop rapides
nb_frames_bas = 0
nb_frames_haut = 0

FRAMES_VALIDATION = 3

# Seuils
# Coude très fléchi (menton au-dessus de la barre)
ANGLE_HAUT = 120
# Bras tendu (suspension)
ANGLE_BAS = 160

# Visibilité minimale
VISIBILITY_MIN = 0.5


# ============================================================
# 3. BOUCLE PRINCIPALE
# ============================================================

while cap.isOpened():

    ret, frame = cap.read()

    if not ret:
        print("Impossible de lire la caméra.")
        break

    # Optionnel : miroir
    frame = cv2.flip(frame, 1)

    # --------------------------------------------------------
    # Conversion BGR -> Image MediaPipe
    # --------------------------------------------------------

    mp_image = creer_image_mp(frame)

    # --------------------------------------------------------
    # Détection
    # --------------------------------------------------------

    resultats = landmarker.detect_for_video(
        mp_image,
        frame_timestamp_ms
    )

    frame_timestamp_ms += 33

    # --------------------------------------------------------
    # Vérifier qu'une personne est détectée
    # --------------------------------------------------------

    if resultats.pose_landmarks:

        landmarks = resultats.pose_landmarks[0]

        # ====================================================
        # LANDMARKS
        # ====================================================

        menton = landmarks[0]

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

        # ====================================================
        # VISIBILITE
        # ====================================================

        bras_d_visible = (
            epaule_d.visibility > VISIBILITY_MIN
            and coude_d.visibility > VISIBILITY_MIN
            and poignet_d.visibility > VISIBILITY_MIN
        )

        bras_g_visible = (
            epaule_g.visibility > VISIBILITY_MIN
            and coude_g.visibility > VISIBILITY_MIN
            and poignet_g.visibility > VISIBILITY_MIN
        )

        angle_coude_d = None
        angle_coude_g = None

        # ====================================================
        # CALCUL ANGLE DROIT
        # ====================================================

        if bras_d_visible:

            angle_coude_d = calculer_angle(
                [epaule_d.x, epaule_d.y],
                [coude_d.x, coude_d.y],
                [poignet_d.x, poignet_d.y]
            )

        # ====================================================
        # CALCUL ANGLE GAUCHE
        # ====================================================

        if bras_g_visible:

            angle_coude_g = calculer_angle(
                [epaule_g.x, epaule_g.y],
                [coude_g.x, coude_g.y],
                [poignet_g.x, poignet_g.y]
            )

        # ====================================================
        # CHOIX DE L'ANGLE
        # ====================================================

        angles = []

        if angle_coude_d is not None:
            angles.append(angle_coude_d)

        if angle_coude_g is not None:
            angles.append(angle_coude_g)

        if len(angles) == 0:
            angle_coude = None
        else:
            # Médiane plus robuste que moyenne
            angle_coude = float(np.median(angles))

        # ====================================================
        # COORDONNEES PIXELS
        # ====================================================

        pts = {
            "menton": point_px(menton, frame),

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

        # ====================================================
        # DESSIN DES BRAS
        # ====================================================

        couleur_ligne = (255, 0, 0)

        cv2.line(
            frame,
            pts["ep_d"],
            pts["co_d"],
            couleur_ligne,
            3
        )

        cv2.line(
            frame,
            pts["co_d"],
            pts["po_d"],
            couleur_ligne,
            3
        )

        cv2.line(
            frame,
            pts["ep_g"],
            pts["co_g"],
            couleur_ligne,
            3
        )

        cv2.line(
            frame,
            pts["co_g"],
            pts["po_g"],
            couleur_ligne,
            3
        )

        # ====================================================
        # DESSIN DU CORPS
        # ====================================================

        cv2.line(
            frame,
            pts["ep_d"],
            pts["hanche_d"],
            couleur_ligne,
            2
        )

        cv2.line(
            frame,
            pts["ep_g"],
            pts["hanche_g"],
            couleur_ligne,
            2
        )

        cv2.line(
            frame,
            pts["hanche_d"],
            pts["cheville_d"],
            couleur_ligne,
            2
        )

        cv2.line(
            frame,
            pts["hanche_g"],
            pts["cheville_g"],
            couleur_ligne,
            2
        )

        # ====================================================
        # POINTS
        # ====================================================

        for cle, p in pts.items():

            if cle == "menton":
                # Le menton est mis en évidence
                cv2.circle(
                    frame,
                    p,
                    8,
                    (0, 255, 255),
                    -1
                )
            else:
                cv2.circle(
                    frame,
                    p,
                    6,
                    (0, 255, 0),
                    -1
                )

        # ====================================================
        # LOGIQUE DE COMPTAGE
        # ====================================================

        if angle_coude is not None:

            # ----------------------------
            # POSITION HAUTE (menton au-dessus de la barre)
            # ----------------------------

            if angle_coude < ANGLE_HAUT:

                nb_frames_haut += 1
                nb_frames_bas = 0

                if nb_frames_haut >= FRAMES_VALIDATION:

                    # Une répétition uniquement si on était en bas
                    if stage == "bas":

                        compteur += 1
                        print(f"✅ Traction validée : {compteur}")

                    stage = "haut"

            # ----------------------------
            # POSITION BASSE (bras tendu)
            # ----------------------------

            elif angle_coude > ANGLE_BAS:

                nb_frames_bas += 1
                nb_frames_haut = 0

                if nb_frames_bas >= FRAMES_VALIDATION:

                    stage = "bas"

            # ----------------------------
            # ZONE INTERMEDIAIRE
            # ----------------------------

            else:

                nb_frames_bas = 0
                nb_frames_haut = 0

            # =================================================
            # AFFICHAGE ANGLE
            # =================================================

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

    # ========================================================
    # INTERFACE
    # ========================================================

    cv2.rectangle(
        frame,
        (0, 0),
        (360, 100),
        (0, 0, 0),
        -1
    )

    cv2.putText(
        frame,
        f"Tractions : {compteur}",
        (15, 40),
        cv2.FONT_HERSHEY_SIMPLEX,
        1.1,
        (255, 255, 255),
        2
    )

    cv2.putText(
        frame,
        f"Position : {stage}",
        (15, 75),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        (255, 255, 255),
        2
    )

    # ========================================================
    # AFFICHAGE
    # ========================================================

    cv2.imshow(
        "RepIA - Detection Tractions",
        frame
    )

    # Quitter avec Q
    if cv2.waitKey(1) & 0xFF == ord("q"):
        break


# ============================================================
# 4. NETTOYAGE
# ============================================================

landmarker.close()
cap.release()
cv2.destroyAllWindows()

print(f"\nNombre total de tractions validees : {compteur}")