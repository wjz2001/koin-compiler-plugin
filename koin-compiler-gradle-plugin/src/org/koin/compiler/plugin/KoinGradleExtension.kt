package org.koin.compiler.plugin

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Gradle extension for configuring the Koin compiler plugin.
 *
 * Usage:
 * ```kotlin
 * koinCompiler {
 *     userLogs = true   // Log component detection and DSL interceptions
 *     debugLogs = true  // Log internal plugin processing for debugging
 * }
 * ```
 */
open class KoinGradleExtension(objectFactory: ObjectFactory) {
    /**
     * Enable user-facing logs.
     * Traces what components are detected and intercepted:
     * - DSL interceptions (single<T>(), factory<T>(), etc.)
     * - Processed annotations (@Singleton, @Factory, @Module, etc.)
     */
    val userLogs: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

    /**
     * Enable debug logs.
     * Traces internal plugin processing for debugging:
     * - FIR phase processing
     * - IR transformation details
     * - Module discovery and registration
     */
    val debugLogs: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

    /**
     * Enable unsafe DSL checks (default: true).
     * When enabled, validates that create() calls inside lambdas are the only instruction.
     * Set to false when migrating from legacy DSL code that has other statements in create lambdas.
     */
    val unsafeDslChecks: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /**
     * Skip injection for parameters with default values (default: true).
     * When enabled, non-nullable parameters with Kotlin default values will use
     * the default value instead of being resolved from the DI container.
     * Nullable parameters are not affected (they still use getOrNull()).
     * Parameters with explicit annotations (@Named, @Qualifier, @InjectedParam, @Property)
     * are always injected regardless of this setting.
     */
    val skipDefaultValues: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /**
     * Enable compile-time dependency safety checks (default: true).
     * When enabled, validates at compile time that all required dependencies are provided
     * within each @Module. Missing non-nullable dependencies without default values
     * will cause a compilation error.
     * Set to false to disable validation (e.g., when dependencies are provided by external modules).
     */
    val compileSafety: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)
}
