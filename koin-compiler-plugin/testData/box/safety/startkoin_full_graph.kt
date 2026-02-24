// FILE: core/Repository.kt
package core

import org.koin.core.annotation.Singleton

@Singleton
class Repository

// FILE: service/Service.kt
package service

import core.Repository
import org.koin.core.annotation.Singleton

@Singleton
class Service(val repo: Repository)

// FILE: modules.kt
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan

// CoreModule scans "core" package → provides Repository
@Module
@ComponentScan("core")
class CoreModule

// ServiceModule scans "service" package → provides Service (needs Repository from CoreModule)
@Module
@ComponentScan("service")
class ServiceModule

// FILE: app.kt
import org.koin.core.annotation.KoinApplication

// A3: startKoin<MyApp> assembles the full graph from @KoinApplication modules
// Validation sees ALL definitions from CoreModule + ServiceModule combined
@KoinApplication(modules = [CoreModule::class, ServiceModule::class])
object MyApp

// FILE: test.kt
import org.koin.plugin.module.dsl.startKoin
import org.koin.core.context.stopKoin

fun box(): String {
    val koin = startKoin<MyApp> {
    }.koin

    val service = koin.get<service.Service>()

    stopKoin()

    return if (service.repo != null) "OK" else "FAIL"
}
