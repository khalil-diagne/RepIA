import os
import urllib.request

import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks.python import vision


# ============================================================
# MODELE
# ============================================================

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/"
    "pose_landmarker/pose_landmarker_lite/float16/1/"
    "pose_landmarker_lite.task"
)

MODEL_PATH = "pose_landmarker_lite.task"


def telecharger_modele():
    """Télécharge le modèle MediaPipe s'il n'est pas déjà présent."""
    if not os.path.exists(MODEL_PATH):
        print("Téléchargement du modèle...")
        urllib.request.urlretrieve(MODEL_URL, MODEL_PATH)
        print("Modèle téléchargé.")


def creer_landmarker():
    """Crée et retourne un PoseLandmarker en mode VIDEO."""
    telecharger_modele()

    BaseOptions = mp.tasks.BaseOptions
    PoseLandmarker = vision.PoseLandmarker
    PoseLandmarkerOptions = vision.PoseLandmarkerOptions
    VisionRunningMode = vision.RunningMode

    options = PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=MODEL_PATH),
        running_mode=VisionRunningMode.VIDEO,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    return PoseLandmarker.create_from_options(options)


# ============================================================
# IMAGE MEDIAPIPE
# ============================================================

def creer_image_mp(frame):
    """Convertit une frame BGR en image MediaPipe (SRGB)."""
    image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    return mp.Image(
        image_format=mp.ImageFormat.SRGB,
        data=image_rgb
    )


# ============================================================
# CALCUL D'ANGLE
# ============================================================

def calculer_angle(a, b, c):
    """
    Calcule l'angle ABC en degrés.
    """

    a = np.array(a, dtype=np.float32)
    b = np.array(b, dtype=np.float32)
    c = np.array(c, dtype=np.float32)

    ba = a - b
    bc = c - b

    norme_ba = np.linalg.norm(ba)
    norme_bc = np.linalg.norm(bc)

    if norme_ba == 0 or norme_bc == 0:
        return 0.0

    cos_angle = np.dot(ba, bc) / (norme_ba * norme_bc)

    # Évite les problèmes numériques
    cos_angle = np.clip(cos_angle, -1.0, 1.0)

    angle = np.degrees(np.arccos(cos_angle))

    return float(angle)


# ============================================================
# COORDONNEES NORMALISEES -> PIXELS
# ============================================================

def point_px(landmark, frame):
    h, w = frame.shape[:2]

    x = int(np.clip(landmark.x, 0, 1) * w)
    y = int(np.clip(landmark.y, 0, 1) * h)

    return x, y