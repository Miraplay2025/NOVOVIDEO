package com.example.data.model

import android.graphics.Matrix
import androidx.compose.ui.graphics.GraphicsLayerScope
import kotlin.math.sin

/**
 * Catálogo de movimentos e animações (IDs 0 a 10)
 * Conforme especificado:
 * 0: Sem Animação (Estático)
 * 1: Pan Left (Desliza da direita para esquerda, revelando a direita)
 * 2: Pan Right (Desliza da esquerda para direita, revelando a esquerda)
 * 3: Tilt Up (Deslocamento vertical de baixo para cima)
 * 4: Tilt Down (Deslocamento vertical de cima para baixo)
 * 5: Zoom In / Dolly In (Aproximação contínua ao centro)
 * 6: Zoom Out / Dolly Out (Afastamento suave a partir do centro)
 * 7: Rotate & Zoom In (Estilo CapCut: Rotação leve de 3° com zoom contínuo)
 * 8: Diagonal Pan (Top-Left to Bottom-Right)
 * 9: Pulse Zoom (Aproximação rápida no centro com suave desaceleração Ease-Out)
 * 10: Horizontal Shake & Pan (Movimento pan dinâmico com micro-estabilização de ação)
 */
data class MovementEffect(
    val id: Int,
    val name: String,
    val description: String,
    val shortBadge: String = "ID $id"
) {
    /**
     * Aplica o efeito de câmera a um GraphicsLayer do Compose para preview em tempo real.
     * @param progress progresso da animação de 0.0f a 1.0f
     */
    fun applyToGraphicsLayer(scope: GraphicsLayerScope, progress: Float) {
        val t = progress.coerceIn(0f, 1f)
        when (id) {
            0 -> {
                // Sem Animação
                scope.scaleX = 1.0f
                scope.scaleY = 1.0f
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            1 -> {
                // Pan Left: Imagem desliza da direita para a esquerda (translationX vai de +10% para -10%)
                val scale = 1.20f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (0.5f - t) * 120f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            2 -> {
                // Pan Right: Imagem desliza da esquerda para a direita
                val scale = 1.20f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (t - 0.5f) * 120f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            3 -> {
                // Tilt Up: Deslocamento vertical de baixo para cima
                val scale = 1.25f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = (0.5f - t) * 120f
                scope.rotationZ = 0f
            }
            4 -> {
                // Tilt Down: Deslocamento vertical de cima para baixo
                val scale = 1.25f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = (t - 0.5f) * 120f
                scope.rotationZ = 0f
            }
            5 -> {
                // Zoom In / Dolly In: Aproximação contínua ao centro (1.0f a 1.35f)
                val scale = 1.0f + (t * 0.35f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            6 -> {
                // Zoom Out / Dolly Out: Afastamento suave a partir do centro (1.35f a 1.0f)
                val scale = 1.35f - (t * 0.35f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            7 -> {
                // Rotate & Zoom In: Estilo CapCut, leve rotação de 3° com zoom contínuo
                val scale = 1.08f + (t * 0.22f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = -1.5f + (t * 3.0f)
            }
            8 -> {
                // Diagonal Pan: Top-Left to Bottom-Right
                val scale = 1.25f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (t - 0.5f) * 100f
                scope.translationY = (t - 0.5f) * 100f
                scope.rotationZ = 0f
            }
            9 -> {
                // Pulse Zoom: Aproximação rápida no centro com suave desaceleração (Ease-Out)
                val easeOut = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
                val scale = 1.0f + (easeOut * 0.35f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            10 -> {
                // Horizontal Shake & Pan: Pan dinâmico com micro-estabilização de ação
                val scale = 1.22f
                val panOffset = (t - 0.5f) * 100f
                val microShake = sin(t * 30f * Math.PI.toFloat()) * 8f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = panOffset + microShake
                scope.translationY = sin(t * 20f * Math.PI.toFloat()) * 3f
                scope.rotationZ = sin(t * 15f * Math.PI.toFloat()) * 0.5f
            }
            else -> {
                scope.scaleX = 1.0f
                scope.scaleY = 1.0f
            }
        }
    }

    /**
     * Aplica o efeito de câmera para a renderização de frames em vídeo (Matrix no Canvas).
     */
    fun applyToMatrix(
        matrix: Matrix,
        progress: Float,
        canvasW: Float,
        canvasH: Float,
        bitmapW: Float,
        bitmapH: Float
    ) {
        val t = progress.coerceIn(0f, 1f)
        matrix.reset()

        // Base fit calculation: fit center
        val scaleFit = minOf(canvasW / bitmapW, canvasH / bitmapH)
        val initialW = bitmapW * scaleFit
        val initialH = bitmapH * scaleFit
        val initialDx = (canvasW - initialW) / 2f
        val initialDy = (canvasH - initialH) / 2f

        var effectScale = 1.0f
        var transXFraction = 0f
        var transYFraction = 0f
        var rotationDegrees = 0f

        when (id) {
            0 -> {
                effectScale = 1.0f
            }
            1 -> {
                // Pan Left: Moves from +15% to -15%
                effectScale = 1.25f
                transXFraction = (0.5f - t) * 0.25f
            }
            2 -> {
                // Pan Right: Moves from -15% to +15%
                effectScale = 1.25f
                transXFraction = (t - 0.5f) * 0.25f
            }
            3 -> {
                // Tilt Up: Moves from +15% to -15%
                effectScale = 1.25f
                transYFraction = (0.5f - t) * 0.25f
            }
            4 -> {
                // Tilt Down: Moves from -15% to +15%
                effectScale = 1.25f
                transYFraction = (t - 0.5f) * 0.25f
            }
            5 -> {
                // Zoom In
                effectScale = 1.0f + (t * 0.35f)
            }
            6 -> {
                // Zoom Out
                effectScale = 1.35f - (t * 0.35f)
            }
            7 -> {
                // Rotate & Zoom In (CapCut)
                effectScale = 1.10f + (t * 0.22f)
                rotationDegrees = -1.5f + (t * 3.0f)
            }
            8 -> {
                // Diagonal Pan
                effectScale = 1.25f
                transXFraction = (t - 0.5f) * 0.20f
                transYFraction = (t - 0.5f) * 0.20f
            }
            9 -> {
                // Pulse Zoom (Ease Out)
                val easeOut = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
                effectScale = 1.0f + (easeOut * 0.35f)
            }
            10 -> {
                // Horizontal Shake & Pan
                effectScale = 1.25f
                val pan = (t - 0.5f) * 0.20f
                val shake = (sin(t * 30f * Math.PI.toFloat()) * 0.02f)
                transXFraction = pan + shake
                transYFraction = (sin(t * 20f * Math.PI.toFloat()) * 0.008f)
                rotationDegrees = sin(t * 15f * Math.PI.toFloat()) * 0.6f
            }
            else -> {
                effectScale = 1.0f
            }
        }

        // Apply transforms centered on canvas
        val totalScale = scaleFit * effectScale
        val scaledW = bitmapW * totalScale
        val scaledH = bitmapH * totalScale

        val centerX = canvasW / 2f
        val centerY = canvasH / 2f

        val transX = (centerX - scaledW / 2f) + (transXFraction * canvasW)
        val transY = (centerY - scaledH / 2f) + (transYFraction * canvasH)

        matrix.postScale(totalScale, totalScale)
        matrix.postTranslate(transX, transY)
        if (rotationDegrees != 0f) {
            matrix.postRotate(rotationDegrees, centerX, centerY)
        }
    }

    companion object {
        val ALL_EFFECTS = listOf(
            MovementEffect(
                id = 0,
                name = "Sem Animação",
                description = "Imagem estática sem qualquer deslocamento ou zoom.",
                shortBadge = "0 • Estático"
            ),
            MovementEffect(
                id = 1,
                name = "Pan Left",
                description = "Desliza suavemente da direita para a esquerda, revelando a direita.",
                shortBadge = "1 • Pan Left"
            ),
            MovementEffect(
                id = 2,
                name = "Pan Right",
                description = "Desliza suavemente da esquerda para a direita, revelando a esquerda.",
                shortBadge = "2 • Pan Right"
            ),
            MovementEffect(
                id = 3,
                name = "Tilt Up",
                description = "Deslocamento vertical de baixo para cima, focado na base subindo até o topo.",
                shortBadge = "3 • Tilt Up"
            ),
            MovementEffect(
                id = 4,
                name = "Tilt Down",
                description = "Deslocamento vertical de cima para baixo, do topo até a base.",
                shortBadge = "4 • Tilt Down"
            ),
            MovementEffect(
                id = 5,
                name = "Zoom In / Dolly In",
                description = "Aproximação contínua em direção ao centro da imagem.",
                shortBadge = "5 • Zoom In"
            ),
            MovementEffect(
                id = 6,
                name = "Zoom Out / Dolly Out",
                description = "Afastamento suave a partir do centro, revelando a cena completa.",
                shortBadge = "6 • Zoom Out"
            ),
            MovementEffect(
                id = 7,
                name = "Rotate & Zoom In",
                description = "Animação estilo CapCut: Rotação leve de 3° com zoom contínuo.",
                shortBadge = "7 • CapCut"
            ),
            MovementEffect(
                id = 8,
                name = "Diagonal Pan",
                description = "Movimento suave em diagonal da ponta superior esquerda à inferior direita.",
                shortBadge = "8 • Diagonal"
            ),
            MovementEffect(
                id = 9,
                name = "Pulse Zoom",
                description = "Aproximação rápida no centro com suave desaceleração (Ease-Out).",
                shortBadge = "9 • Pulse"
            ),
            MovementEffect(
                id = 10,
                name = "Horizontal Shake & Pan",
                description = "Movimento pan dinâmico com micro-estabilização de ação.",
                shortBadge = "10 • Dynamic"
            )
        )

        fun findById(id: Int): MovementEffect? {
            return ALL_EFFECTS.firstOrNull { it.id == id }
        }
    }
}
