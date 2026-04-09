// FILE: data/CacheImpl.kt
package data

import org.koin.core.annotation.Singleton
import org.koin.core.annotation.Named

interface Cache

// Implementation lives in `data` package with a String qualifier.
// Cross-module discovery must propagate the @Named qualifier through hint metadata
// so that consumers in another package can resolve their @Named-annotated dependency.
@Singleton
@Named("local")
class LocalCache : Cache

@Singleton
@Named("remote")
class RemoteCache : Cache

// FILE: ui/CacheManager.kt
package ui

import data.Cache
import org.koin.core.annotation.Singleton
import org.koin.core.annotation.Named

// Consumer requests qualified dependencies. Because UiModule and DataModule are
// separate @Configuration groups with separate @ComponentScan packages, the qualifier
// of LocalCache/RemoteCache must be carried through `componentscan_*` hint params —
// not by re-reading annotations on the cross-module class.
@Singleton
class CacheManager(
    @Named("local") val local: Cache,
    @Named("remote") val remote: Cache
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
