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
import org.koin.core.annotation.Configuration

// CoreModule scans "core" package → provides Repository
@Module
@ComponentScan("core")
@Configuration
class CoreModule

// ServiceModule scans "service" package → provides Service (needs Repository from CoreModule)
// A2: Repository is visible because CoreModule shares the same @Configuration("default") label
@Module
@ComponentScan("service")
@Configuration
class ServiceModule

// FILE: test.kt
import org.koin.dsl.koinApplication

fun box(): String {
    val koin = koinApplication {
        modules(CoreModule().module(), ServiceModule().module())
    }.koin

    val service = koin.get<service.Service>()
    return if (service.repo != null) "OK" else "FAIL"
}
