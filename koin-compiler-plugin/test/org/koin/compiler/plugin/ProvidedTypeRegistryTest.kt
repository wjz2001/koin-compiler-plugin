package org.koin.compiler.plugin

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ProvidedTypeRegistry].
 *
 * Tests the registry for types marked with @Provided, which are skipped
 * during compile-time safety validation.
 */
class ProvidedTypeRegistryTest {

    @BeforeEach
    fun setUp() {
        ProvidedTypeRegistry.clear()
    }

    @AfterEach
    fun tearDown() {
        ProvidedTypeRegistry.clear()
    }

    @Test
    fun `registered type is provided`() {
        ProvidedTypeRegistry.register("android.content.Context")
        assertTrue(ProvidedTypeRegistry.isProvided("android.content.Context"))
    }

    @Test
    fun `unregistered type is not provided`() {
        assertFalse(ProvidedTypeRegistry.isProvided("com.example.Unknown"))
    }

    @Test
    fun `clear resets all registered types`() {
        ProvidedTypeRegistry.register("android.content.Context")
        ProvidedTypeRegistry.register("android.app.Activity")

        ProvidedTypeRegistry.clear()

        assertFalse(ProvidedTypeRegistry.isProvided("android.content.Context"))
        assertFalse(ProvidedTypeRegistry.isProvided("android.app.Activity"))
    }

    @Test
    fun `multiple types can be registered`() {
        ProvidedTypeRegistry.register("android.content.Context")
        ProvidedTypeRegistry.register("android.app.Activity")
        ProvidedTypeRegistry.register("com.example.CustomProvider")

        assertTrue(ProvidedTypeRegistry.isProvided("android.content.Context"))
        assertTrue(ProvidedTypeRegistry.isProvided("android.app.Activity"))
        assertTrue(ProvidedTypeRegistry.isProvided("com.example.CustomProvider"))
    }

    @Test
    fun `duplicate registration is idempotent`() {
        ProvidedTypeRegistry.register("android.content.Context")
        ProvidedTypeRegistry.register("android.content.Context")

        assertTrue(ProvidedTypeRegistry.isProvided("android.content.Context"))
    }
}
