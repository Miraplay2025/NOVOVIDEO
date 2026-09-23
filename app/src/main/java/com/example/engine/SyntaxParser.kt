package com.example.engine

import com.example.data.model.ParsedAnimationConfig

sealed class SyntaxParseResult {
    data class Success(val configs: List<ParsedAnimationConfig>) : SyntaxParseResult()
    data class Error(
        val message: String,
        val faultySnippet: String? = null
    ) : SyntaxParseResult()
}

sealed class TransitionValidationResult {
    data class Success(val transitionIds: List<Int>) : TransitionValidationResult()
    data class Error(
        val message: String,
        val faultyId: String? = null
    ) : TransitionValidationResult()
}

data class RandomPromptResult(
    val movementSyntaxText: String,
    val transitionIdsText: String
)

object SyntaxParser {

    /**
     * Valida e interpreta a sintaxe textual fornecida pelo usuário.
     * Exemplo de formato:
     * "IMAGEM 1 + MOVIMENTO 1 + 6.0s, IMAGEM 2 + MOVIMENTO 2 + 4.0s"
     *
     * @param text O texto digitado pelo usuário.
     * @param totalProjectImages Quantidade total de imagens cadastradas no projeto.
     */
    fun parseAndValidate(text: String, totalProjectImages: Int): SyntaxParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return SyntaxParseResult.Error(
                "A caixa de texto de sintaxe está vazia. Por favor configure as instruções de movimento para as imagens."
            )
        }

        if (totalProjectImages == 0) {
            return SyntaxParseResult.Error(
                "O projeto não possui imagens importadas. Adicione imagens antes de configurar a sintaxe."
            )
        }

        // Separa itens por vírgula ou por quebra de linha
        val rawTokens = trimmed
            .split(Regex("[,;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawTokens.isEmpty()) {
            return SyntaxParseResult.Error(
                "Nenhum comando de imagem detectado na sintaxe informada."
            )
        }

        val parsedConfigs = mutableListOf<ParsedAnimationConfig>()
        val seenImageIndices = mutableSetOf<Int>()

        for (token in rawTokens) {
            // Divide o comando por '+'
            val parts = token.split("+").map { it.trim() }
            if (parts.size != 3) {
                return SyntaxParseResult.Error(
                    message = "Formato inválido no trecho: '$token'. O formato exigido é: IMAGEM X + MOVIMENTO Y + Z.Zs",
                    faultySnippet = token
                )
            }

            val imagePart = parts[0]
            val movementPart = parts[1]
            val durationPart = parts[2]

            // 1. Validação de IMAGEM
            val imageMatch = Regex("(?i)IMAGEM?\\s*(\\d+)").matchEntire(imagePart)
            if (imageMatch == null) {
                return SyntaxParseResult.Error(
                    message = "Identificador de imagem inválido em '$imagePart'. Esperado: 'IMAGEM X' (ex: IMAGEM 1).",
                    faultySnippet = token
                )
            }
            val imageIndex = imageMatch.groupValues[1].toIntOrNull()
            if (imageIndex == null || imageIndex <= 0) {
                return SyntaxParseResult.Error(
                    message = "Número de imagem inválido em '$imagePart'.",
                    faultySnippet = token
                )
            }

            // Validação de Existência de Imagem: Se citar IMAGEM 8 e projeto só tiver 5 imagens
            if (imageIndex > totalProjectImages) {
                return SyntaxParseResult.Error(
                    message = "Validação de Existência de Imagem falhou: IMAGEM $imageIndex não existe no projeto. O projeto possui apenas $totalProjectImages imagem(ns).",
                    faultySnippet = token
                )
            }

            // Validação de Duplicidade
            if (seenImageIndices.contains(imageIndex)) {
                return SyntaxParseResult.Error(
                    message = "Validação de Duplicidade falhou: IMAGEM $imageIndex foi configurada mais de uma vez.",
                    faultySnippet = token
                )
            }
            seenImageIndices.add(imageIndex)

            // 2. Validação de MOVIMENTO
            val movementMatch = Regex("(?i)MOVIMENTO?\\s*(\\d+)").matchEntire(movementPart)
            if (movementMatch == null) {
                return SyntaxParseResult.Error(
                    message = "Identificador de movimento inválido em '$movementPart'. Esperado: 'MOVIMENTO Y' (ex: MOVIMENTO 1).",
                    faultySnippet = token
                )
            }
            val movementId = movementMatch.groupValues[1].toIntOrNull()
            if (movementId == null) {
                return SyntaxParseResult.Error(
                    message = "Número de movimento inválido em '$movementPart'.",
                    faultySnippet = token
                )
            }

            // Validação de Existência de Movimento: IDs de 0 a 26 (11 originais + 16 novos profissionais)
            if (movementId !in 0..com.example.data.model.MovementEffect.MAX_ID) {
                return SyntaxParseResult.Error(
                    message = "Validação de Existência de Movimento falhou: MOVIMENTO $movementId é inexistente. Só existem animações de 0 a ${com.example.data.model.MovementEffect.MAX_ID}.",
                    faultySnippet = token
                )
            }

            // 3. Validação de DURAÇÃO (deve ter 's' no final)
            if (!durationPart.endsWith("s", ignoreCase = true)) {
                return SyntaxParseResult.Error(
                    message = "Validação de Duração falhou na IMAGEM $imageIndex: o valor '$durationPart' deve conter o sufixo 's' (ex: 4.0s ou 6.0s).",
                    faultySnippet = token
                )
            }

            val durationNumberStr = durationPart.dropLast(1).trim()
            val durationVal = durationNumberStr.toFloatOrNull()
            if (durationVal == null || durationVal <= 0.1f) {
                return SyntaxParseResult.Error(
                    message = "Validação de Duração falhou na IMAGEM $imageIndex: tempo inválido '$durationPart'. O tempo deve ser numérico maior que 0.1s.",
                    faultySnippet = token
                )
            }

            parsedConfigs.add(
                ParsedAnimationConfig(
                    imageIndex = imageIndex,
                    movementId = movementId,
                    durationSeconds = durationVal,
                    rawText = token
                )
            )
        }

        // Validação de Cobertura Total: TODAS as imagens existentes devem obrigatoriamente ter uma linha configurada
        if (parsedConfigs.size != totalProjectImages) {
            val missing = (1..totalProjectImages).filter { it !in seenImageIndices }
            return SyntaxParseResult.Error(
                message = "Validação de Cobertura Total falhou: O projeto possui $totalProjectImages imagem(ns), mas apenas ${parsedConfigs.size} foram configuradas. Imagens ausentes: ${missing.joinToString { "IMAGEM $it" }}."
            )
        }

        // Ordena pela numeração da imagem (1, 2, 3...)
        val sortedConfigs = parsedConfigs.sortedBy { it.imageIndex }
        return SyntaxParseResult.Success(sortedConfigs)
    }

    /**
     * Gera texto padrão de sintaxe para facilitar a inicialização pelo usuário.
     */
    fun generateDefaultSyntax(
        totalImages: Int,
        defaultMovementId: Int = 1,
        defaultDurationSeconds: Float = 6.0f
    ): String {
        if (totalImages <= 0) return ""
        return (1..totalImages).joinToString(",\n") { index ->
            // Varia sutilmente os movimentos para enriquecer a experiência padrão
            val mov = if (defaultMovementId == 1) {
                ((index - 1) % 10) + 1
            } else {
                defaultMovementId
            }
            val dur = String.format(java.util.Locale.US, "%.1fs", defaultDurationSeconds)
            "IMAGEM $index + MOVIMENTO $mov + $dur"
        }
    }

    /**
     * Valida rígida e individualmente se todos os IDs informados existem na lista (1 a 20, ou 0).
     */
    fun validateTransitionIds(text: String): TransitionValidationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TransitionValidationResult.Error(
                "O campo de IDs de transições está vazio. Informe os IDs de 1 a 20 separados por vírgula (ex: 1, 4, 2, 8)."
            )
        }

        val tokens = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return TransitionValidationResult.Error(
                "Nenhum ID de transição encontrado. Informe IDs de 1 a 20 separados por vírgula."
            )
        }

        val validIds = mutableListOf<Int>()
        for (token in tokens) {
            val id = token.toIntOrNull()
            if (id == null) {
                return TransitionValidationResult.Error(
                    message = "ID de transição inválido: '$token'. Informe apenas números de 1 a 20 (ou 0 para sem transição) separados por vírgula.",
                    faultyId = token
                )
            }
            if (id !in 0..20) {
                return TransitionValidationResult.Error(
                    message = "ID de transição inexistente: '$token'. Os IDs válidos são de 1 a 20 inspirados no CapCut (ou 0 para sem transição).",
                    faultyId = token
                )
            }
            validIds.add(id)
        }

        return TransitionValidationResult.Success(validIds)
    }

    /**
     * Gera prompts totalmente aleatórios conforme Requisito 4:
     * - Movimentos de câmera aleatórios para cada imagem (0 a 10)
     * - Duração de cada imagem aleatória no intervalo de 5.0s a 10.0s
     * - IDs de transições gerados de forma totalmente aleatória (1 a 20)
     */
    fun generateRandomPrompts(totalImages: Int): RandomPromptResult {
        if (totalImages <= 0) {
            return RandomPromptResult(
                movementSyntaxText = "",
                transitionIdsText = "1, 4, 2, 8"
            )
        }

        val random = java.util.Random()
        val syntaxLines = (1..totalImages).map { index ->
            val randomMov = random.nextInt(com.example.data.model.MovementEffect.MAX_ID + 1) // 0 a 26
            val randomDuration = 5.0f + (random.nextInt(51) / 10.0f) // 5.0s a 10.0s (passos de 0.1s)
            val durFormatted = String.format(java.util.Locale.US, "%.1fs", randomDuration)
            "IMAGEM $index + MOVIMENTO $randomMov + $durFormatted"
        }

        val transitionCount = (totalImages - 1).coerceAtLeast(1)
        val randomTransitionIds = (1..transitionCount).map {
            random.nextInt(20) + 1 // 1 a 20
        }

        return RandomPromptResult(
            movementSyntaxText = syntaxLines.joinToString(",\n"),
            transitionIdsText = randomTransitionIds.joinToString(", ")
        )
    }
}
