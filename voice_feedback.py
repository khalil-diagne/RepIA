# -*- coding: utf-8 -*-
"""
voice_feedback.py — Feedback vocal wolof pour RepIA (version Python / desktop).

Guide l'utilisateur pendant les séances avec une voix sénégalaise parlant wolof,
ou une voix française par défaut si aucune voix wolof n'est installée sur le système.

Principe :
    1. Détection d'une voix TTS wolof native (pyttsx3 / SAPI5) au runtime.
    2. Si présente : on lit le texte wolof standard (orthographe correcte ñ, ë...).
    3. Sinon : la voix française lit une version "phonétique française" du wolof,
       pré-écrite pour que la prononciation tombe le plus juste possible.

La synthèse s'exécute dans un thread dédié (file d'attente + engine unique) afin de
ne JAMAIS bloquer la boucle de détection MediaPipe.
"""

import logging
import queue
import threading
from enum import Enum
from typing import Dict, List, Optional, Tuple

try:
    import pyttsx3
    PYTTSX3_DISPONIBLE = True
except ImportError:  # pragma: no cover
    pyttsx3 = None
    PYTTSX3_DISPONIBLE = False


LOGGER = logging.getLogger("voice_feedback")


# ============================================================
# MODES DE VOIX
# ============================================================
class ModeVoix(Enum):
    """
    Mode de synthèse vocale.

    AUTO                 : wolof natif si une voix wolof existe, sinon repli phonétique.
    WOLOF_NATIF          : force le texte wolof standard (et la voix wolof si possible).
    WOLOF_FR_FALLBACK    : force la voix française avec la version phonétique française.
    FRANCAIS             : force la version entièrement française.
    """

    AUTO = "auto"
    WOLOF_NATIF = "wolof_native"
    WOLOF_FR_FALLBACK = "wolof_fr_fallback"
    FRANCAIS = "francais"


# ============================================================
# PHRASES (les mêmes que dans la version web app.js)
# Chaque événement possède trois versions :
#   - "wolof"               : wolof standard (voix wolof native).
#   - "wolof_fr_phonetique" : wolof réécrit pour une voix française (ñ -> gn, x->j, etc.).
#   - "francais"            : français simple (mode forcé FRANCAIS).
# ============================================================
PHRASES: Dict[str, Dict[str, str]] = {
    "hors_cadre": {
        "wolof": "Dugil ci biir kamer bi, duñu la gis",
        "wolof_fr_phonetique": "Douguil ci bir kamer bi, dougnou la guiss",
        "francais": "Positionne-toi dans le cadre",
    },
    "corps_partiel": {
        "wolof": "Réculal sëpp, duñu mën a gis sa yaram bépp",
        "wolof_fr_phonetique": "Rékoulal sèp, dougnou mène a guiss sa yaram bép",
        "francais": "Recule, ton corps entier doit être visible",
    },
    "gainage": {
        "wolof": "Dugil ci posisyon gainage bi, nekk fa rekk",
        "wolof_fr_phonetique": "Douguil ci pozisyon gainage bi, nèk fa rèk",
        "francais": "Entre dans la position de gainage",
    },
    "serie_finie": {
        "wolof": "Séri bi jeex na! Noppal bu baax",
        "wolof_fr_phonetique": "Séri bi djèx na! Nopal bou bâx",
        "francais": "Série terminée. Repos.",
    },
    "seance_finie": {
        "wolof": "Séance bi jeex na! Jeral sa bop",
        "wolof_fr_phonetique": "Séans bi djèx na! Djéral sa bop",
        "francais": "Séance terminée. Bravo !",
    },
    "repos_fini": {
        "wolof": "Nopp bi jeex na! Duggil ci biir",
        "wolof_fr_phonetique": "Nop bi djèx na! Dougil ci bir",
        "francais": "Repos terminé",
    },
}


# Preférence pour une voix féminine (nom complet ou marqueur de genre SAPI5).
VOIX_FEMININES = ("hortense", "julie", "paulina", "zira", "female", "fé")


def _correspond_langue(voix: object, prefixe: str) -> bool:
    """
    Vérifie si une voix correspond au préfixe de langue voulu ("fr", "wo").

    pyttsx3 expose la langue de façon inégale (parfois vide) selon le moteur :
    on cherche donc dans l'id, le nom et la liste des langues de la voix.
    """
    if voix is None:
        return False
    id_v = getattr(voix, "id", "") or ""
    nom = getattr(voix, "name", "") or ""
    langues = getattr(voix, "languages", None) or []
    chaine = (id_v + "|" + nom + "|" + " | ".join(str(l) for l in langues)).lower()
    return prefixe.lower() in chaine.replace("_", "-")


def _prefere_feminin(voix: object) -> bool:
    """Retourne True si la voix semble féminine (d'après son nom ou son genre)."""
    nom = (getattr(voix, "name", "") or "").lower()
    genre = (getattr(voix, "gender", None) or "").lower()
    return any(mot in (nom + genre) for mot in VOIX_FEMININES)


class VoixFeedback:
    """
    Gestionnaire de synthèse vocale wolof, exécuté en arrière-plan.

    Toutes les appels publics ont un retour immédiat : la parole est mise en file
    d'attente puis lue par un unique thread worker (un seul moteur pyttsx3, car
    l'engine n'est pas sûr en accès concurrent).
    """

    _instance = None
    _verrou_instance = threading.Lock()

    def __init__(
        self,
        mode: ModeVoix = ModeVoix.AUTO,
        debit: int = 175,
        volume: float = 1.0,
    ) -> None:
        """
        Args:
            mode:     Configuration initiale (défaut : AUTO).
            debit:    Débit modéré à légèrement rapide (comme le wolof parlé).
            volume:   0.0 à 1.0.
        """
        self._mode = ModeVoix(mode)
        self._debit = debit
        self._volume = volume
        self._file_attente: "queue.Queue[str]" = queue.Queue()
        self._moteur = None
        self._arret = threading.Event()

        # Le moteur est créé DANS le thread worker (pyttsx3 préfère un usage mono-thread).
        self._thread = threading.Thread(
            target=self._boucle_travail,
            name="voix-feedback",
            daemon=True,
        )
        self._thread.start()

    # ----------------------------------------------------------
    # Fabrique singleton (confort d'usage)
    # ----------------------------------------------------------
    @classmethod
    def instance(cls) -> "VoixFeedback":
        """Retourne l'instance unique du gestionnaire (créée à la demande)."""
        with cls._verrou_instance:
            if cls._instance is None:
                cls._instance = cls()
            return cls._instance

    # ----------------------------------------------------------
    # API publique
    # ----------------------------------------------------------
    def set_mode(self, mode: ModeVoix) -> None:
        """Force manuellement le mode de synthèse (remplace la détection auto)."""
        self._mode = ModeVoix(mode)

    def speak(self, event_key: str) -> bool:
        """
        Annonce l'événement de façon asynchrone (ne bloque jamais l'appelant).

        Args:
            event_key: clé dans PHRASES ("hors_cadre", "gainage", "serie_finie"...).

        Returns:
            True si l'annonce a été mise en file, False si le moteur est absent
            ou si la clé est inconnue.
        """
        if event_key not in PHRASES:
            LOGGER.warning("Clé d'événement inconnue : %r", event_key)
            return False
        if not PYTTSX3_DISPONIBLE:
            LOGGER.warning(
                "pyttsx3 absent : installez-le avec 'pip install pyttsx3' "
                "pour activer la voix (version desktop)."
            )
            return False
        self._file_attente.put(event_key)
        return True

    def fermer(self) -> None:
        """Arrête le thread worker et libère le moteur TTS."""
        self._arret.set()
        if self._thread is not None:
            self._thread.join(timeout=2.0)
        if self._moteur is not None:
            try:
                self._moteur.stop()
            except Exception:  # pragma: no cover
                pass

    # ----------------------------------------------------------
    # Thread worker
    # ----------------------------------------------------------
    def _boucle_travail(self) -> None:
        """Crée le moteur puis lit les événements de la file, un par un."""
        if not PYTTSX3_DISPONIBLE:
            return
        try:
            self._moteur = pyttsx3.init()
        except Exception as exc:  # pragma: no cover
            LOGGER.warning("Impossible d'initialiser pyttsx3 : %s", exc)
            return

        while not self._arret.is_set():
            try:
                cle = self._file_attente.get(timeout=0.5)
            except queue.Empty:
                continue
            try:
                self._traiter(cle)
            except Exception as exc:  # pragma: no cover
                LOGGER.warning("Erreur pendant la synthèse de %r : %s", cle, exc)

        # Le worker s'arrête : on coupe toute parole en cours.
        self._moteur.stop()

    def _traiter(self, cle: str) -> None:
        """
        Joue l'annonce : coupe la précédente, choisit la voix et dit le texte.

        L'appel stop() avant say() garantit qu'une nouvelle alerte interrompt
        immédiatement l'ancienne (important pour les alertes répétitives d'angle).
        """
        texte = self._choisir_texte(cle)
        if texte is None:
            return

        self._moteur.stop()  # interrompt une annonce encore en cours
        self._regler_voix(self._mode_effectif())
        try:
            self._moteur.setProperty("rate", self._debit)
        except Exception:  # pragma: no cover
            pass
        try:
            self._moteur.setProperty("volume", self._volume)
        except Exception:  # pragma: no cover
            pass

        self._moteur.say(texte)
        self._moteur.runAndWait()  # bloque uniquement le thread worker

    # ----------------------------------------------------------
    # Logique de sélection du texte et de la voix
    # ----------------------------------------------------------
    def _mode_effectif(self) -> ModeVoix:
        """Retourne le mode réel selon la config et les voix disponibles."""
        if self._mode == ModeVoix.AUTO:
            return ModeVoix.WOLOF_NATIF if self._voix_wolof() else ModeVoix.WOLOF_FR_FALLBACK
        return self._mode

    def _choisir_texte(self, cle: str) -> Optional[str]:
        """Sélectionne la version de la phrase adaptée au mode effectif."""
        mode = self._mode_effectif()
        if mode == ModeVoix.FRANCAIS:
            return PHRASES[cle]["francais"]
        if mode == ModeVoix.WOLOF_NATIF:
            return PHRASES[cle]["wolof"]
        return PHRASES[cle]["wolof_fr_phonetique"]

    def _voix_wolof(self) -> bool:
        """Détecte au runtime la présence d'une voix wolof dans le moteur."""
        if self._moteur is None:
            return False
        return any(_correspond_langue(v, "wo") for v in self._voix_systeme())

    def _voix_systeme(self) -> List[object]:
        """Liste des voix du système (retourne [] si indisponible)."""
        try:
            return list(self._moteur.getProperty("voices"))
        except Exception:  # pragma: no cover
            return []

    def _regler_voix(self, mode: ModeVoix) -> None:
        """
        Sélectionne la voix sur le moteur :
          - wolof natif  -> première voix wolof trouvée.
          - sinon        -> voix française, en préférant une voix féminine.
        """
        voix = self._voix_systeme()
        if not voix:
            return

        cible = "wo" if mode == ModeVoix.WOLOF_NATIF else "fr"
        matchs = [v for v in voix if _correspond_langue(v, cible)]

        # On préfère une voix féminine (ton chaleureux, proche d'une conversation
        # de tous les jours) puis on revient à n'importe quelle voix de la langue.
        choix = None
        for v in matchs:
            if _prefere_feminin(v):
                choix = v
                break
        if choix is None and matchs:
            choix = matchs[0]

        if choix is not None:
            try:
                self._moteur.setProperty("voice", choix.id)
            except Exception:  # pragma: no cover
                pass


# ============================================================
# FONCTIONS DE CONFORT (API simplifiée)
# ============================================================
def speak(event_key: str, mode: Optional[ModeVoix] = None) -> bool:
    """
    Annonce un événement en wolof (asynchrone, non bloquant).

    Args:
        event_key: clé parmi les événements de PHRASES.
        mode:      mode forcé optionnel (ex. ModeVoix.WOLOF_FR_FALLBACK).
    """
    gestionnaire = VoixFeedback.instance()
    if mode is not None:
        gestionnaire.set_mode(mode)
    return gestionnaire.speak(event_key)


def verifier_voix_wolof() -> Tuple[bool, Optional[str]]:
    """
    Vérifie rapidement si le système possède une voix wolof (test séparé,
    sans toucher au worker en cours).

    Returns:
        (disponible, identifiant de la voix) ou (False, None).
    """
    if not PYTTSX3_DISPONIBLE:
        return False, None
    try:
        moteur_test = pyttsx3.init()
        for voix in moteur_test.getProperty("voices"):
            if _correspond_langue(voix, "wo"):
                moteur_test.stop()
                return True, getattr(voix, "id", None)
        moteur_test.stop()
    except Exception:  # pragma: no cover
        pass
    return False, None


# ============================================================
# TEST RAPIDE :  python voice_feedback.py [cle] [mode]
#   Exemple :     python voice_feedback.py gainage wolof_fr_fallback
# ============================================================
if __name__ == "__main__":
    import sys

    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")

    cle_test = sys.argv[1] if len(sys.argv) > 1 else "serie_finie"
    mode_test = sys.argv[2] if len(sys.argv) > 2 else None
    if mode_test is not None:
        try:
            mode_test = ModeVoix(mode_test)
        except ValueError:
            LOGGER.warning("Mode inconnu (auto|wolof_native|wolof_fr_fallback|francais)")

    dispo, vo_id = verifier_voix_wolof()
    print("Voix wolof détectée :", "OUI" if dispo else "NON", vo_id or "")

    if cle_test not in PHRASES:
        LOGGER.error("Clé inconnue. Disponibles : %s", ", ".join(PHRASES))
        raise SystemExit(1)

    if mode_test is None:
        print(f"Mode par défaut : {ModeVoix.AUTO.value}")
    ok = speak(cle_test, mode_test)
    print("Annonce mise en file :", ok, " pour ", cle_test)

    # Le worker est un daemon : on laisse quelques secondes pour entendre le résultat.
    import time

    time.sleep(6)