// RUN_PIPELINE_TILL: BACKEND
// FILE: core/Repository.kt
package core

import org.koin.core.annotation.Singleton

@Singleton
class Repository

// FILE: service/Service.kt
package service

import core.Repository
import org.koin.core.annotation.Singleton

// Service needs Repository, but ServiceModule has a different @Configuration label
@Singleton
class Service(val repo: Repository)

// FILE: modules.kt
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration

// CoreModule is in "core" configuration group
@Module
@ComponentScan("core")
@Configuration("core")
class CoreModule

// ServiceModule is in "service" configuration group — different label, so Repository is NOT visible
@Module
@ComponentScan("service")
@Configuration("service")
class ServiceModule

/* GENERATED_FIR_TAGS: classDeclaration, primaryConstructor, propertyDeclaration */
