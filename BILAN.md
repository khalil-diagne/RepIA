# RepIA — Bilan du projet

## 1. Contexte et objectif

**RepIA** est un compteur de répétitions d'exercices de musculation basé sur la
vision par ordinateur. L'objectif est de détecter en temps réel le corps de
l'utilisateur via une webcam et de compter automatiquement les répétitions de
trois exercices :

- **Pompes** (push-ups)
- **Dips** (aux barres)
- **Tractions** (chin-ups / pull-ups)

L'application est accessible depuis un navigateur web : le flux vidéo est
affiché en direct avec un compteur, et l'utilisateur peut choisir l'exercice
qu'il souhaite réaliser.

---

## 2. Technologies utilisées

| Technologie   | Rôle                                          |
|---------------|-----------------------------------------------|
| Python 3.13   | Langage principal (anciennement)              |
| Flask         | Serveur local de preview (statique)           |
| JavaScript / HTML / CSS | Interface web + toute la détection      |
| MediaPipe (Tasks Vision JS, v1.0.1) | PoseLandmarker Lite côté navigateur |
| WebAssembly   | Moteur de calcul MediaPipe dans le navigateur |
| Vercel        | Hébergement HTTPS (déploiement)               |

Modèle utilisé : `pose_landmarker_lite.task` (MediaPipe, mode VIDEO).

**Note d'architecture** : depuis la v2 du projet, **toute la détection s'exécute
dans le navigateur** (PC comme téléphone). Le serveur ne sert plus que des
fichiers statiques (page, scripts, modèle). Cela rend le service **infiniement
extensible** : chaque visiteur fait tourner MediaPipe sur **son propre appareil**,
le serveur ne reçoit **aucune image** et ne peut donc pas saturer.

---

## 3. Architecture du projet

```
RepIA/
├── index.html                  # Page web (statique, sert aussi sur Vercel)
├── static/
│   └── js/
│       ├── exercices.js        # Config des exercices (seuils, stages)
│       ├── detecteur.js        # Logique de détection/comptage (portée en JS)
│       └── app.js              # Caméra, boucle temps réel, dessin, UI
├── pose_landmarker_lite.task   # Modèle MediaPipe
├── app.py                      # Serveur Flask local de preview uniquement
├── vercel.json                 # Cache headers pour le déploiement Vercel
├── .vercelignore               # Fichiers exclus du déploiement
├── pompes.py                   # Script standalone (legacy, affichage OpenCV)
├── dips.py                     # Script standalone (legacy)
├── tractions.py                # Script standalone (legacy)
├── exercices_logic.py          # Logique Python (legacy, base du port JS)
└── utils.py                    # Fonctions Python (legacy)
```

### Rôle des fichiers

- **`index.html`** : page web statique. Hébergée telle quelle sur Vercel (HTTPS
  automatique, donc caméra autorisée partout), et servie localement par `app.py`
  ou `python -m http.server`.
- **`static/js/exercices.js`** : configuration centralisée (mêmes seuils que la
  version Python) : pompes/dips (coude < 95° = fléchi, > 155° = étendu), tractions
  (120°/ 160°, logique inversée, menton mis en évidence).
- **`static/js/detecteur.js`** : port fidèle de `exercices_logic.py` — calcul
  d'angle (arccos + médiane des deux coudes), filtre de visibilité (0.5),
  validation sur ~3 frames consécutives, machine à stages et compteur.
- **`static/js/app.js`** : initialise MediaPipe (`PoseLandmarker`, mode VIDEO,
  delegate GPU avec repli CPU), ouvre la caméra (`getUserMedia`,
  avant/arrière), et boucle en `requestVideoFrameCallback` : détection → comptage
  → dessin squelette + HUD sur `<canvas>` → mise à jour des stats.
- **`app.py`** : minimum Flask pour une preview locale (`http://localhost:5000`).
  Aucune détection serveur, aucune dépendance lourde.

---

## 4. Comment fonctionne la détection

1. **Caméra** : ouverte dans le navigateur via `getUserMedia` (rétro = `user`,
   arrière = `environment`), flux en miroir pour la caméra avant.
2. **Modèle** : `PoseLandmarker` (mode VIDEO), chargé une seule fois (modèle
   servi par le site, moteur WASM via CDN jsDelivr).
3. **Détection** : `detectForVideo(video, timestamp)` renvoie les 33 landmarks
   du corps. Les frames sont traitées en synchronisation avec la lecture vidéo
   (`requestVideoFrameCallback`).
4. **Visibilité** : seuls les bras dont les points ont une visibilité > 0.5
   sont pris en compte.
5. **Angle du coude** : calculé sur les deux bras (épaule–coude–poignet),
   médiane retenue.
6. **Comptage** : transitions de stage validées sur 3 frames consécutives :
   - Pompes / Dips : coude < 95° → "bas" ; > 155° → "haut" ; +1 en revenant
     en "haut".
   - Tractions : logique inversée (bras fléchi = "haut"), menton mis en évidence.
7. **Affichage** : squelette + HUD (nom, compteur, position, angle) dessinés sur
   un `<canvas>` recouvrant la vidéo.

---

## 5. Choix techniques

### Détection 100 % client (option retenue)
Chaque visiteur exécute MediaPipe **sur son appareil**. Le serveur ne traite
aucune image ⇒ **aucune saturation possible**, quel que soit le nombre
d'utilisateurs, et aucune dépendance entre eux. C'est l'évolution majeure de la
v2 : elle remplace l'ancienne architecture « caméra serveur + MJPEG +
polling /stats ».

### Optimisation de la v2.1 (inference dans un Web Worker)
L'inférence MediaPipe s'exécute dans un **Web Worker** (`static/js/worker.js`)
afin de ne pas bloquer le thread de rendu sur les appareils lents. La caméra est
également plafonnée à 1280px et les frames sont redimensionnées à 640px avant
d'être envoyées au worker. Si le worker est encore occupé, la frame est
ignorée (pas de file qui accumule de la latence). Repli automatique sur le
thread principal si le worker ne se charge pas.

### Modèle cross-platform
Le fichier `.task` MediaPipe fonctionne à l'identique en Python et en JS. Il est
servi par le site ; le moteur WASM + lib JS viennent du CDN jsDelivr (version
pinnée `1.0.1`).

### HTTPS requis (caméra)
`getUserMedia` exige un contexte sécurisé. Vercel fournit le HTTPS
automatiquement ⇒ accessible et fonctionnel depuis tout téléphone. En local,
`http://localhost` est considéré sûr par les navigateurs.

### Python en legacy
`pompes.py`, `dips.py`, `tractions.py`, `exercices_logic.py`, `utils.py` restent
disponibles comme scripts standalone (fenêtre OpenCV), mais ne sont plus
utilisés par l'application web.

---

## 6. Difficultés rencontrées et solutions

### 6.1. Plantage de l'encodage console (Windows)
Le `print("✅ ...")` avec l'emoji plantait en console Windows (codec cp1252).
**Solution** : messages ASCII simples.

### 6.2. Serveur lent au démarrage / thread de capture bloqué
Le modèle MediaPipe chargé au niveau du module retardait `app.run()`.
**Solution** : initialisation déplacée dans le thread de capture
(architecture v1, désormais supprimée car la v2 n'a plus de thread python).

### 6.3. Duplication des scripts d'exercices
Trois scripts quasi identiques. **Solution** : config centralisée `EXERCICES`
(portée en JS dans la v2) + changement d'exercice instantané sans rechargement.

### 6.4. Saturation serveur en usage multi-utilisateurs (motivation v2)
L'architecture v1 envoyait chaque frame des téléphones vers le serveur (coût
CPU MediaPipe par frame). **Solution de la v2** : déplacement complet de la
détection dans le navigateur, le serveur ne servant plus que du statique.

---

## 7. Résultats et tests

- Détection et comptage en temps réel dans le navigateur (testé localement sur
  `localhost` : caméra, squelette, compteur, changement d'exercice, reset).
- Changement d'exercice **instantané** (aucun rechargement de modèle).
- Aucune donnée image ne transite par le serveur : privacité + zéro charge.
- Scripts Python standalone conservés et fonctionnels.

---

## 8. Pistes d'amélioration

- **Modèle Full/Heavy** : permettre de choisir la précision (`pose_landmarker_full`)
  pour les appareils puissants.
- **Historique des séances** : enregistrer répétitions/durée/date (localStorage
  ou petit backend dédié).
- **Calibration** : ajuster les seuils d'angle depuis l'interface.
- **Arrêt/verrouillage du compteur** pour éviter un démarrage anticipé.
- **Robustesse** : contrôler que les hanches sont visibles avant de compter
  (détection partielle).

---

## 9. Commandes utiles

```bash
# Preview locale (sans Flask)
python -m http.server 5000
# -> http://localhost:5000

# Preview locale (via Flask)
.\venv\Scripts\python.exe app.py
# -> http://localhost:5000

# Déploiement sur Vercel (depuis le dossier du projet)
npx vercel          # aperçu
npx vercel --prod   # production
# -> https://<projet>.vercel.app   (HTTPS automatique)
```

Scripts standalone (legacy, fenêtre OpenCV) :

```bash
.\venv\Scripts\python.exe pompes.py     # ou dips.py / tractions.py
```