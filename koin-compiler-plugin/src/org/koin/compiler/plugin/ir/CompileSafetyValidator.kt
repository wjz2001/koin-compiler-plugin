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
            KoinPluginLogger.debug { "  Safety check for $moduleName: deferred to A3 (non-@Configuration module with cross-module definitions)" }
            return
        }

        var hasUnresolvableSiblings = false
        // Include definitions from included modules (transitive availability at runtime)
        val allVisibleDefinitions = buildList {
            addAll(definitions)
            // A1: Explicit includes
            for (includedModuleClass in moduleClass.includedModules) {
                val includedModule = allLocalModuleClasses.find {
                    it.irClass.fqNameWhenAvailable == includedModuleClass.fqNameWhenAvailable
                }
                if (includedModule != null) {
                    addAll(collectAllDefinitions(includedModule))
                }
            }
            // A2: If this module is @Configuration, include sibling modules from the same group
            val configLabels = extractConfigurationLabels(moduleClass.irClass)
            if (configLabels.isNotEmpty()) {
                val siblingModuleNames = KoinConfigurationRegistry.getModuleClassNamesForLabels(configLabels)
                KoinPluginLogger.debug { "  A2: $moduleName labels=$configLabels, registry has ${siblingModuleNames.size} siblings" }
                for (siblingName in siblingModuleNames) {
                    val siblingModule = allLocalModuleClasses.find {
                        it.irClass.fqNameWhenAvailable?.asString() == siblingName
                    }
                    if (siblingModule != null && siblingModule != moduleClass) {
                        // Local sibling — collect all its definitions
                        addAll(collectAllDefinitions(siblingModule))
                    } else if (siblingModule == null) {
                        // Cross-Gradle-module sibling — resolve from dependency JAR
                        KoinPluginLogger.debug { "    A2 resolving cross-module sibling: $siblingName" }
                        val result = collectDefinitionsFromDependencyModule(siblingName)
                        KoinPluginLogger.debug { "    -> got ${result.definitions.size} definitions (complete=${result.isComplete})" }
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
            bindingRegistry.validateModule(moduleName, allVisibleDefinitions, parameterAnalyzer, qualifierExtractor)
            // Track this module as validated so A3 won't re-check its definitions
            if (moduleFqName != null) {
                validatedModuleFqNames.add(moduleFqName)
            }
        } else {
            KoinPluginLogger.debug { "  Safety check for $moduleName: deferred to A3 (cross-module siblings not fully resolvable)" }
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
        // Collect definitions from all modules in the graph
        // Track which definitions need validation (not already validated at A2)
        var hasUnresolvableModules = false
        val allDefinitions = mutableListOf<Definition>()
        val definitionsToValidate = mutableListOf<Definition>()

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
                if (!alreadyValidated) {
                    definitionsToValidate.addAll(defs)
                }
            } else {
                // Cross-module @Configuration module from dependency JAR
                KoinPluginLogger.debug { "  -> A3: resolving dependency module $moduleFqName" }
                val result = getDefinitionsForDependencyModule(moduleFqName)
                if (!result.isComplete) {
                    hasUnresolvableModules = true
                    KoinPluginLogger.debug { "  -> A3: $moduleFqName not fully resolvable (has @ComponentScan from different compilation)" }
                } else {
                    KoinPluginLogger.debug { "  -> A3: $moduleFqName contributed ${result.definitions.size} definitions (complete)" }
                }
                allDefinitions.addAll(result.definitions)
                if (!alreadyValidated) {
                    definitionsToValidate.addAll(result.definitions)
                }
            }
        }

        if (allDefinitions.isEmpty()) return

        if (hasUnresolvableModules) {
            KoinPluginLogger.debug { "  -> Full-graph validation for $appName: skipped (some modules not fully resolvable from hints)" }
            return
        }

        if (definitionsToValidate.isEmpty()) {
            KoinPluginLogger.debug { "  -> Full-graph validation for $appName: skipped (all ${allDefinitions.size} definitions already validated at A2)" }
            return
        }

        KoinPluginLogger.debug { "  -> Full-graph validation for $appName: ${definitionsToValidate.size}/${allDefinitions.size} definitions to validate from ${allModuleIrClasses.size} modules" }

        val fullGraphRegistry = BindingRegistry()
        val errorCount = fullGraphRegistry.validateModule(
            "$appName (startKoin)",
            allDefinitions,
            parameterAnalyzer,
            qualifierExtractor,
            definitionsToValidate
        )
        if (errorCount > 0) {
            KoinPluginLogger.debug { "  -> Full-graph validation found $errorCount errors" }
        }
    }
}
