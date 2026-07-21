package io.codecks.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDraftValidatorTest {
    private val validator = ActionDraftValidator()

    @Test
    fun validate_acceptsSupportedDraft() {
        val definition = validDefinition()

        assertEquals(ValidationResult.Valid, validator.validate(definition))
    }

    @Test
    fun validate_rejectsMalformedModelShape() {
        val malformed = listOf(
            validDefinition().copy(schemaVersion = 999),
            validDefinition().copy(steps = listOf(ActionStep("bad", "made_up"))),
            validDefinition().copy(steps = listOf(ActionStep("url", ActionStepTypes.OpenUrl, url = "file:///tmp/a"))),
            validDefinition().copy(target = TargetSelector.DeviceId("")),
            validDefinition().copy(steps = listOf(ActionStep("delay", ActionStepTypes.Delay, delayMs = Long.MAX_VALUE))),
            validDefinition().copy(steps = listOf(ActionStep("retry", ActionStepTypes.Delay, retry = RetryPolicy(99, 0)))),
            validDefinition().copy(steps = listOf(ActionStep("shell", ActionStepTypes.Shell, value = "rm -rf /"))),
            validDefinition().copy(safety = SafetyMetadata(SafetyLevel.Dangerous), steps = listOf(ActionStep("x", ActionStepTypes.Delay))),
            validDefinition().copy(steps = listOf(ActionStep("open", ActionStepTypes.OpenUrl))),
            validDefinition().copy(steps = listOf(ActionStep("delay", ActionStepTypes.Delay))),
            validDefinition().copy(steps = listOf(ActionStep("clip", ActionStepTypes.ClipboardText))),
            validDefinition().copy(variables = listOf(ActionVariable("name", "Name"), ActionVariable("name", "Other"))),
            validDefinition().copy(templates = listOf(ActionTemplate("t", "one"), ActionTemplate("t", "two"))),
        )

        malformed.forEach { definition ->
            assertTrue(validator.validate(definition) is ValidationResult.Invalid)
        }
    }

    @Test
    fun validate_acceptsGoldenStepFixturesForCurrentSchema() {
        val steps = listOf(
            ActionStep("open", ActionStepTypes.OpenUrl, url = "https://example.com"),
            ActionStep("delay", ActionStepTypes.Delay, delayMs = 500),
            ActionStep("key", ActionStepTypes.HidKey, value = "ENTER", requiredCapabilities = listOf(ActionCapability.HidKeyboard)),
            ActionStep("clip", ActionStepTypes.ClipboardText, value = "hello", requiredCapabilities = listOf(ActionCapability.Clipboard)),
            ActionStep("ssh", ActionStepTypes.SshAction, value = "finder", requiredCapabilities = listOf(ActionCapability.Ssh)),
            ActionStep("shell", ActionStepTypes.Shell, value = "echo ok", requiredCapabilities = listOf(ActionCapability.Advanced)),
        )

        steps.forEach { step ->
            assertEquals(ValidationResult.Valid, validator.validate(validDefinition().copy(steps = listOf(step))))
        }
    }

    private fun validDefinition(): ActionDefinition =
        ActionDefinition(
            id = "open-example",
            title = "Open Example",
            requiredCapabilities = listOf(ActionCapability.Browser),
            steps = listOf(ActionStep("open", ActionStepTypes.OpenUrl, url = "https://example.com")),
        )
}
