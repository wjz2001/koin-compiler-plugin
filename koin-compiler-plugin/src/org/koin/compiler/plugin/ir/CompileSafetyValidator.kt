package org.koin.compiler.plugin.ir

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.koin.compiler.plugin.KoinConfigurationRegistry
import org.koin.compiler.plugin.KoinPluginLogger

/**
 * Orchestrates compile-time safety validation for Koin definitions.
 *
 * Validates that all required dependencies are provided within visible scope:
 * - **A2 (per-module)**: Each @Configuration module's definitions are validated against
 *   its own definitions + included modules + @Configuration sibling modules.
 * - **A3 (full-graph)**: All definitions from all modules assembled at startKoin<T>()
 *   are validated together — but only for modules not already validated at A2.
 *
 * The actual matching logic lives in [BindingRegistry]. This class handles the
 * orchestration: deciding what to validate, collecting cross-module definitions,
 * and deciding when to defer validation.
 */
class CompileSafetyValidator(
    private val qualifierExtractor: QualifierExtractor
) {
    private val parameterAnalyzer = ParameterAnalyzer(qualifierExtractor)
    private val bindingRegistry = BindingRegistry()

    /** FQNames of modules whose definitions were already validated at A2. */
    private val validatedModuleFqNames = mutableSetOf<String>()

    /**
     * A2: Validate a single module's definitions against visible dependencies.
     *
     * Handles:
     * - Non-@Configuration modules with cross-module definitions → defer to A3
     * - A1: Explicit module includes
     * - A2: @Configuration sibling discovery (local + cross-module JAR)
     * - Unresolvable cross-module siblings → defer to A3
     *
     * @param moduleClass The module being validated
     * @param definitions Definitions already collected for this module
     * @param allLocalModuleClasses All module classes in the current compilation unit
     * @param collectAllDefinitions Callback to collect definitions for a local module
     * @param localDefinitionClasses All definition classes from the current compilation (for cross-module detection)
     * @param collectDefinitionsFromDependencyModule Callback to resolve definitions from a dependency JAR module
     */
    fun validateModule(
        moduleClass: ModuleClass,
        definitions: List<Definition>,
        allLocalModuleClasses: List<ModuleClass>,
        collectAllDefinitions: (ModuleClass) -> List<Definition>,
        localDefinitionClasses: List<DefinitionClass>,
        collectDefinitionsFromDependencyModule: (String) -> DependencyModuleResult
    ) {
        val moduleName = moduleClass.irClass.name.asString()
        val moduleFqName = moduleClass.irClass.fqNameWhenAvailable?.asString()
        val isConfigurationModule = hasConfigurationAnnotation(moduleClass.irClass)
        KoinPluginLogger.debug { "── A2 Safety: $moduleName ──" }
        KoinPluginLogger.debug { "  module: $moduleFqName" }
        KoinPluginLogger.debug { "  definitions: ${definitions.size}, isConfiguration: $isConfigurationModule" }

        // Non-@Configuration modules that discover cross-module definitions (from hints)
        // are part of a multi-module graph. Their dependencies may be provided by
        // @Configuration siblings or parent modules, so standalone A2 validation
        // would produce false positives. Defer to A3 (startKoin) for full-graph validation.
        val hasCrossModuleDefinitions = definitions.any { def ->
            when (def) {
                is Definition.ExternalFunctionDef -> true
                is Definition.ClassDef -> {
                    // A class def from hints won't be in the local definitionClasses
                    localDefinitionClasses.none { it.irClass.fqNameWhenAvailable == def.irClass.fqNameWhenAvailable }
                }
                else -> false
            }
        }
        if (!isConfigurationModule && hasCrossModuleDefinitions) {
            KoinPluginLogger.debug { "  -> DEFERRED to A3 (non-@Configuration with cross-module definitions)" }
            return
        }

        var hasUnresolvableSiblings = false
        var includedCount = 0
        var siblingCount = 0
        // Include definitions from included modules (transitive availability at runtime)
        val allVisibleDefinitions = buildList {
            addAll(definitions)
            // A1: Explicit includes
            if (moduleClass.includedModules.isNotEmpty()) {
                KoinPluginLogger.debug { "  A1 includes:" }
            }
            for (includedModuleClass in moduleClass.includedModules) {
                val includedModule = allLocalModuleClasses.find {
                    it.irClass.fqNameWhenAvailable == includedModuleClass.fqNameWhenAvailable
                }
                if (includedModule != null) {
                    val includedDefs = collectAllDefinitions(includedModule)
                    KoinPluginLogger.debug { "    + ${includedModuleClass.name}: ${includedDefs.size} definitions" }
                    includedCount += includedDefs.size
                    addAll(includedDefs)
                } else {
                    KoinPluginLogger.debug { "    + ${includedModuleClass.name}: not found in local modules" }
                }
            }
            // A2: If this module is @Configuration, include sibling modules from the same group
            val configLabels = extractConfigurationLabels(moduleClass.irClass)
            if (configLabels.isNotEmpty()) {
                val siblingModuleNames = KoinConfigurationRegistry.getModuleClassNamesForLabels(configLabels)
                KoinPluginLogger.debug { "  A2 @Configuration siblings (labels=$configLabels):" }
                KoinPluginLogger.debug { "    registry has ${siblingModuleNames.size} modules for these labels" }
                for (siblingName in siblingModuleNames) {
                    val siblingModule = allLocalModuleClasses.find {
                        it.irClass.fqNameWhenAvailable?.asString() == siblingName
                    }
                    if (siblingModule != null && siblingModule != moduleClass) {
                        // Local sibling — collect all its definitions
                        val siblingDefs = collectAllDefinitions(siblingModule)
                        KoinPluginLogger.debug { "    + $siblingName (local): ${siblingDefs.size} definitions" }
                        siblingCount += siblingDefs.size
                        addAll(siblingDefs)
                    } else if (siblingModule == null) {
                        // Cross-Gradle-module sibling — resolve from dependency JAR
                        KoinPluginLogger.debug { "    + $siblingName (cross-module JAR):" }
                        val result = collectDefinitionsFromDependencyModule(siblingName)
                        KoinPluginLogger.debug { "      -> ${result.definitions.size} definitions (complete=${result.isComplete})" }
                        siblingCount += result.definitions.size
                        if (!result.isComplete) {
                            hasUnresolvableSiblings = true
                        }
                        addAll(result.definitions)
                    }
                }
            }
        }

        // If some @Configuration siblings are in different Gradle modules and their
        // definitions can't be discovered (no hints for locally-scanned classes,
        // class not on classpath), skip per-module validation. The full-graph
        // validation (A3: startKoin<T>) will catch real missing dependencies.
        if (!hasUnresolvableSiblings) {
            KoinPluginLogger.debug { "  visibility summary: own=${definitions.size} + includes=$includedCount + siblings=$siblingCount = ${allVisibleDefinitions.size} total" }
            KoinPluginLogger.debug { "  -> VALIDATING..." }
            bindingRegistry.validateModule(moduleName, allVisibleDefinitions, parameterAnalyzer, qualifierExtractor)
            // Track this module as validated so A3 won't re-check its definitions
            if (moduleFqName != null) {
                validatedModuleFqNames.add(moduleFqName)
                KoinPluginLogger.debug { "  -> $moduleName marked as validated (won't re-check at A3)" }
            }
        } else {
            KoinPluginLogger.debug { "  -> DEFERRED to A3 (cross-module siblings not fully resolvable)" }
        }
    }

    /**
     * A3: Validate the full assembled module graph at the startKoin entry point.
     *
     * Collects ALL definitions from ALL discovered modules and validates that
     * every required dependency is satisfied somewhere in the combined graph.
     * Skips re-validating definitions from modules already validated at A2.
     *
     * @param appName Application class name (for error messages)
     * @param allModuleIrClasses All module IrClasses discovered for this startKoin call
     * @param collectedModuleClasses Local module classes from annotation processing
     * @param getDefinitionsForModule Callback to get definitions for a local module
     * @param getDefinitionsForDependencyModule Callback to get definitions from a dependency JAR module
     */
    fun validateFullGraph(
        appName: String,
        allModuleIrClasses: List<IrClass>,
        collectedModuleClasses: List<ModuleClass>,
        getDefinitionsForModule: (ModuleClass) -> List<Definition>,
        getDefinitionsForDependencyModule: (String) -> DependencyModuleResult
    ) {
        KoinPluginLogger.debug { "── A3 Safety: Full-graph for $appName ──" }
        KoinPluginLogger.debug { "  modules in graph: ${allModuleIrClasses.size}" }
        KoinPluginLogger.debug { "  already validated at A2: ${validatedModuleFqNames.size} modules" }
        if (validatedModuleFqNames.isNotEmpty()) {
            KoinPluginLogger.debug { "    ${validatedModuleFqNames.joinToString(", ")}" }
        }

        // Collect definitions from all modules in the graph
        // Track which definitions need validation (not already validated at A2)
        var hasUnresolvableModules = false
        val allDefinitions = mutableListOf<Definition>()
        val definitionsToValidate = mutableListOf<Definition>()

        KoinPluginLogger.debug { "  collecting definitions from all modules:" }
        for (moduleIrClass in allModuleIrClasses) {
            val moduleFqName = moduleIrClass.fqNameWhenAvailable?.asString() ?: continue
            val moduleClass = collectedModuleClasses.find {
                it.irClass.fqNameWhenAvailable == moduleIrClass.fqNameWhenAvailable
            }

            val alreadyValidated = moduleFqName in validatedModuleFqNames

            if (moduleClass != null) {
                // Local module — collect all definitions (includes cross-module hints)
                val defs = getDefinitionsForModule(moduleClass)
                allDefinitions.addAll(defs)
                val status = if (alreadyValidated) "provider-only (validated at A2)" else "needs validation"
                KoinPluginLogger.debug { "    + $moduleFqName (local): ${defs.size} definitions [$status]" }
                if (!alreadyValidated) {
                    definitionsToValidate.addAll(defs)
                }
            } else {
                // Cross-module @Configuration module from dependency JAR
                KoinPluginLogger.debug { "    + $moduleFqName (dependency JAR):" }
                val result = getDefinitionsForDependencyModule(moduleFqName)
                if (!result.isComplete) {
                    hasUnresolvableModules = true
                    KoinPluginLogger.debug { "      -> ${result.definitions.size} definitions (INCOMPLETE - has @ComponentScan)" }
                } else {
                    val status = if (alreadyValidated) "provider-only" else "needs validation"
                    KoinPluginLogger.debug { "      -> ${result.definitions.size} definitions (complete) [$status]" }
                }
                allDefinitions.addAll(result.definitions)
                if (!alreadyValidated) {
                    definitionsToValidate.addAll(result.definitions)
                }
            }
        }

        if (allDefinitions.isEmpty()) {
            KoinPluginLogger.debug { "  -> SKIPPED (no definitions found)" }
            return
        }

        if (hasUnresolvableModules) {
            KoinPluginLogger.debug { "  -> SKIPPED (some modules not fully resolvable from hints)" }
            return
        }

        if (definitionsToValidate.isEmpty()) {
            KoinPluginLogger.debug { "  -> SKIPPED (all ${allDefinitions.size} definitions already validated at A2)" }
            return
        }

        KoinPluginLogger.debug { "  graph summary: ${definitionsToValidate.size} to validate, ${allDefinitions.size} total providers, from ${allModuleIrClasses.size} modules" }
        KoinPluginLogger.debug { "  -> VALIDATING..." }

        val fullGraphRegistry = BindingRegistry()
        val errorCount = fullGraphRegistry.validateModule(
            "$appName (startKoin)",
            allDefinitions,
            parameterAnalyzer,
            qualifierExtractor,
            definitionsToValidate
        )
        if (errorCount > 0) {
            KoinPluginLogger.debug { "  -> DONE: $errorCount errors found" }
        } else {
            KoinPluginLogger.debug { "  -> DONE: all dependencies satisfied" }
        }
    }
}
