// FILE: data/CacheImpl.kt
// FLAKY: Like several other tests in this folder, this occasionally fails when run as part of the
// full ./test.sh suite (non-deterministic) but passes deterministically alone. Reproduces on baseline,
// not introduced by current work. Suspected cause: Kotlin compiler test framework state pollution
// within a shared JVM. See property_value_ok.kt for the longer note.
package data

import org.koin.core.annotation.Singleton
import org.koin.core.annotation.Qualifier

interface Cache

// Marker types used as type qualifiers
class LocalQualifier
class RemoteQualifier

// Type-qualified implementations live in the `data` package.
// Cross-module discovery must propagate the @Qualifier(Type::class) qualifier through hint
// metadata so consumers in another package can resolve their @Qualifier-annotated dependencies.
// This is the "TypeQualifier" sibling of qualifier_cross_module.kt (which covers StringQualifier).
@Singleton
@Qualifier(LocalQualifier::class)
class LocalCache : Cache

@Singleton
@Qualifier(RemoteQualifier::class)
class RemoteCache : Cache

// FILE: ui/CacheManager.kt
package ui

import data.Cache
import data.LocalQualifier
import data.RemoteQualifier
import org.koin.core.annotation.Singleton
import org.koin.core.annotation.Qualifier

// Consumer requests type-qualified dependencies. Because UiModule and DataModule are
// separate @Configuration groups with separate @ComponentScan packages, the qualifier type
// must be carried through `componentscan_*` hint params (`qualifierType` param) — not by
// re-reading annotations on the cross-module class.
@Singleton
class CacheManager(
    @Qualifier(LocalQualifier::class) val local: Cache,
    @Qualifier(RemoteQualifier::class) val remote: Cache
)

// FILE: modules.kt
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration

@Module
@ComponentScan("data")
@Configuration
class DataModule

@Module
@ComponentScan("ui")
@Configuration
class UiModule

// FILE: test.kt
import org.koin.dsl.koinApplication

fun box(): String {
    val koin = koinApplication {
        modules(DataModule().module(), UiModule().module())
    }.koin

    val manager = koin.get<ui.CacheManager>()

    val localOk = manager.local is data.LocalCache
    val remoteOk = manager.remote is data.RemoteCache

    return if (localOk && remoteOk) "OK" else "FAIL: local=$localOk, remote=$remoteOk"
}
