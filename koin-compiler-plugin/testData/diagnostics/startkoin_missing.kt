// RUN_PIPELINE_TILL: BACKEND
// FILE: service/Service.kt
package service

import org.koin.core.annotation.Singleton

// Service needs MissingDep which is NOT provided by any module
@Singleton
class Service(val missing: MissingDep)

class MissingDep

// FILE: modules.kt
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication

@Module
@ComponentScan("service")
class ServiceModule

// A3: startKoin<MyApp> assembles the full graph — MissingDep is still absent
@KoinApplication(modules = [ServiceModule::class])
object MyApp

// FILE: main.kt
import org.koin.plugin.module.dsl.startKoin

fun main() {
    startKoin<MyApp> { }
}

/* GENERATED_FIR_TAGS: classDeclaration, objectDeclaration, primaryConstructor, propertyDeclaration */
