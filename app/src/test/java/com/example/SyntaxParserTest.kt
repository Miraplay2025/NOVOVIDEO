package com.example

import com.example.engine.SyntaxParseResult
import com.example.engine.SyntaxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxParserTest {

    @Test
    fun testValidSyntaxParsing() {
        val input = "IMAGEM 1 + MOVIMENTO 1 + 6.0s, IMAGEM 2 + MOVIMENTO 2 + 4.0s, IMAGEM 3 + MOVIMENTO 5 + 3.5s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 3)

        assertTrue(result is SyntaxParseResult.Success)
        val configs = (result as SyntaxParseResult.Success).configs
        assertEquals(3, configs.size)

        assertEquals(1, configs[0].imageIndex)
        assertEquals(1, configs[0].movementId)
        assertEquals(6.0f, configs[0].durationSeconds, 0.01f)

        assertEquals(2, configs[1].imageIndex)
        assertEquals(2, configs[1].movementId)
        assertEquals(4.0f, configs[1].durationSeconds, 0.01f)

        assertEquals(3, configs[2].imageIndex)
        assertEquals(5, configs[2].movementId)
        assertEquals(3.5f, configs[2].durationSeconds, 0.01f)
    }

    @Test
    fun testMissingDurationSuffixError() {
        val input = "IMAGEM 1 + MOVIMENTO 1 + 6.0"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 1)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("'s'"))
    }

    @Test
    fun testInvalidMovementRangeError() {
        val input = "IMAGEM 1 + MOVIMENTO 15 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 1)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("0 a 10"))
    }

    @Test
    fun testNonExistentImageIndexError() {
        val input = "IMAGEM 4 + MOVIMENTO 1 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 2)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("não existe no projeto"))
    }

    @Test
    fun testMissingImagesCoverageError() {
        val input = "IMAGEM 1 + MOVIMENTO 1 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 3)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("Cobertura Total"))
    }
}
