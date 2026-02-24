package featureutil

import org.koin.core.annotation.Singleton

/**
 * A utility class provided by a top-level function in a different package.
 * This package is NOT covered by FeatureModule's @ComponentScan("feature"),
 * so the function hint will be generated as "orphan" for cross-module discovery.
 */
data class FeatureConfig(val name: String = "feature", val version: Int = 1)

@Singleton
fun provideFeatureConfig(): FeatureConfig = FeatureConfig()
