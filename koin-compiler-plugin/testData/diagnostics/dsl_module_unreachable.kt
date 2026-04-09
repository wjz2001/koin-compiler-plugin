// RUN_PIPELINE_TILL: BACKEND
// FILE: test.kt
// FLAKY: Occasionally fails when run as part of the full ./test.sh suite (non-deterministic).
// Passes when run alone. Reproduces on baseline, not introduced by current work. Suspected cause:
// Kotlin compiler test framework state pollution within a shared JVM.
import org.koin.dsl.module
import org.koin.core.context.startKoin
import org.koin.plugin.module.dsl.single

class Repository
class Service(val repo: Repository)
class Tracker

// trackerModule exists but is NOT loaded — error even though no one depends on Tracker
val trackerModule = module {
    single<Tracker>()
}

val coreModule = module {
    single<Repository>()
}

val appModule = module {
    includes(coreModule)
    single<Service>()
}

fun main() {
    startKoin {
        modules(appModule)
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, lambdaLiteral, primaryConstructor, propertyDeclaration */
