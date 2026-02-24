package examples.crossmodule

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

/**
 * Test cross-module top-level function discovery.
 * This module scans both "featureutil" (cross-module function hint) and "examples.crossmodule" (local).
 * The function hint from sample-feature-module should make FeatureConfig visible for safety checks.
 */
@Module
@ComponentScan("featureutil", "examples.crossmodule")
class CrossModuleFunctionModule

/**
 * A service that depends on FeatureConfig from the cross-module function.
 * This validates that the function hint makes FeatureConfig visible for safety checks.
 */
@Singleton
class CrossModuleConsumer(val config: featureutil.FeatureConfig)

@KoinApplication(modules = [CrossModuleFunctionModule::class])
interface CrossModuleFunctionApp
