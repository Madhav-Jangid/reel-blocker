package com.reellock.reellock.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidatorTest {

    private fun baseConfig() = CommitmentConfig.DEFAULT

    @Test
    fun `default config is valid`() {
        val result = ConfigValidator.validate(baseConfig())
        assertEquals(ConfigValidator.ValidationResult.Valid, result)
    }

    @Test
    fun `allowance below minimum is rejected`() {
        val config = baseConfig().copy(reelsAllowanceSeconds = 10)
        val result = ConfigValidator.validate(config)
        assertTrue(result is ConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `allowance larger than window is rejected`() {
        val config = baseConfig().copy(reelsAllowanceSeconds = 20_000, windowSeconds = 18_000)
        val result = ConfigValidator.validate(config)
        assertTrue(result is ConfigValidator.ValidationResult.Invalid)
        val errors = (result as ConfigValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("cannot exceed windowSeconds") })
    }

    @Test
    fun `commitment days out of range is rejected`() {
        val config = baseConfig().copy(commitmentDays = 0)
        assertTrue(ConfigValidator.validate(config) is ConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `blank target package is rejected`() {
        val config = baseConfig().copy(targetPackage = "  ")
        assertTrue(ConfigValidator.validate(config) is ConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `allowance equal to window is allowed`() {
        val config = baseConfig().copy(reelsAllowanceSeconds = 18_000, windowSeconds = 18_000)
        assertEquals(ConfigValidator.ValidationResult.Valid, ConfigValidator.validate(config))
    }
}
