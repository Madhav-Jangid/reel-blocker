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
    fun `empty monitoredApps is rejected`() {
        val config = baseConfig().copy(monitoredApps = emptySet())
        assertTrue(ConfigValidator.validate(config) is ConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `an unsupported package in monitoredApps is rejected`() {
        val config = baseConfig().copy(monitoredApps = setOf("com.some.other.app"))
        assertTrue(ConfigValidator.validate(config) is ConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `both supported apps monitored together is valid`() {
        val config = baseConfig().copy(
            monitoredApps = setOf(
                com.reellock.reellock.classifier.TargetApp.INSTAGRAM.packageName,
                com.reellock.reellock.classifier.TargetApp.YOUTUBE.packageName,
            ),
        )
        assertEquals(ConfigValidator.ValidationResult.Valid, ConfigValidator.validate(config))
    }

    @Test
    fun `allowance equal to window is allowed`() {
        val config = baseConfig().copy(reelsAllowanceSeconds = 18_000, windowSeconds = 18_000)
        assertEquals(ConfigValidator.ValidationResult.Valid, ConfigValidator.validate(config))
    }
}
