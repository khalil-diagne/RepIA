package com.repia.app.ml

import kotlin.math.acos
import kotlin.math.hypot

data class Point(val x: Float, val y: Float, val visibility: Float)

data class Stats(
    val typeExo: String,
    val nom: String,
    val compteur: Int,
    val stage: String,
    val angle: Float?,
    val positionValide: Boolean
)

data class ExoConfig(
    val nom: String,
    val stageInitial: String,
    val angleFlechi: Float,
    val angleEtendu: Float,
    val hautQuandFlechi: Boolean,
    val articulation: String
)

object Exercises {
    val EXMAP = mapOf(
        "pompes" to ExoConfig("Pompes", "haut", 95f, 155f, false, "coude"),
        "dips" to ExoConfig("Dips", "haut", 95f, 155f, false, "coude"),
        "tractions" to ExoConfig("Tractions", "bas", 120f, 160f, true, "coude"),
        "squats" to ExoConfig("Squats", "haut", 85f, 165f, false, "genou")
    )
}

class RepetitionCounter(private val typeExo: String) {
    private val config = Exercises.EXMAP[typeExo] ?: Exercises.EXMAP["pompes"]!!
    private var compteur = 0
    private var stage = config.stageInitial
    private var nbFramesFlechi = 0
    private var nbFramesEtendu = 0
    private var currentAngle: Float? = null
    var pause = false

    companion object {
        const val FRAMES_VALIDATION = 3
        const val VISIBILITY_MIN = 0.5f

        val ARTICULATIONS = mapOf(
            "coude" to listOf(listOf(12, 14, 16), listOf(11, 13, 15)),
            "genou" to listOf(listOf(24, 26, 28), listOf(23, 25, 27))
        )
    }

    fun reset() {
        compteur = 0
        stage = config.stageInitial
        nbFramesFlechi = 0
        nbFramesEtendu = 0
        currentAngle = null
    }

    fun analyser(landmarks: List<Point>?): Stats {
        currentAngle = null
        if (landmarks == null || landmarks.isEmpty()) {
            return stats()
        }

        val voies = ARTICULATIONS[config.articulation] ?: ARTICULATIONS["coude"]!!
        val angles = mutableListOf<Float>()

        for (voie in voies) {
            if (voie[0] < landmarks.size && voie[1] < landmarks.size && voie[2] < landmarks.size) {
                val a = landmarks[voie[0]]
                val b = landmarks[voie[1]]
                val d = landmarks[voie[2]]
                if (a.visibility > VISIBILITY_MIN && b.visibility > VISIBILITY_MIN && d.visibility > VISIBILITY_MIN) {
                    angles.add(calculerAngle(a, b, d))
                }
            }
        }

        val angle = if (angles.isEmpty()) null else mediane(angles)
        currentAngle = angle

        if (angle != null) {
            if (angle < config.angleFlechi) {
                nbFramesFlechi++
                nbFramesEtendu = 0
                if (nbFramesFlechi >= FRAMES_VALIDATION) {
                    transition(if (config.hautQuandFlechi) "haut" else "bas")
                }
            } else if (angle > config.angleEtendu) {
                nbFramesEtendu++
                nbFramesFlechi = 0
                if (nbFramesEtendu >= FRAMES_VALIDATION) {
                    transition(if (config.hautQuandFlechi) "bas" else "haut")
                }
            } else {
                nbFramesFlechi = 0
                nbFramesEtendu = 0
            }
        }

        return stats()
    }

    private fun transition(nouveauStage: String) {
        if (!pause && nouveauStage == "haut" && stage == "bas") {
            compteur++
        }
        stage = nouveauStage
    }

    private fun calculerAngle(a: Point, b: Point, c: Point): Float {
        val baX = a.x - b.x
        val baY = a.y - b.y
        val bcX = c.x - b.x
        val bcY = c.y - b.y

        val normeBA = hypot(baX, baY)
        val normeBC = hypot(bcX, bcY)

        if (normeBA == 0f || normeBC == 0f) return 0f

        var cosAngle = (baX * bcX + baY * bcY) / (normeBA * normeBC)
        cosAngle = cosAngle.coerceIn(-1.0f, 1.0f)

        return (acos(cosAngle.toDouble()) * 180.0 / Math.PI).toFloat()
    }

    private fun mediane(valeurs: List<Float>): Float {
        val tri = valeurs.sorted()
        val n = tri.size
        return if (n % 2 == 1) {
            tri[(n - 1) / 2]
        } else {
            (tri[n / 2 - 1] + tri[n / 2]) / 2f
        }
    }

    fun stats(): Stats {
        return Stats(
            typeExo = typeExo,
            nom = config.nom,
            compteur = compteur,
            stage = stage,
            angle = currentAngle,
            positionValide = true
        )
    }
}
