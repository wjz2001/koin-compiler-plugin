// FILE: test.kt
// FLAKY: Occasionally fails when run as part of the full ./test.sh suite (non-deterministic).
// Passes when run alone. Reproduces on baseline, not introduced by current work. Suspected cause:
// Kotlin compiler test framework state pollution within a shared JVM.
import org.koin.dsl.koinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Singleton
import org.koin.core.annotation.Named
import org.koin.core.qualifier.named

@Module
@ComponentScan
class TestModule

@Singleton
@Named("special")
class SpecialDependency

@Singleton
class Consumer(@Named("special") val dep: SpecialDependency)

fun box(): String {
    val koin = koinApplication {
        modules(TestModule().module())
    }.koin

    val special = koin.get<SpecialDependency>(named("special"))
    val consumer = koin.get<Consumer>()

    return if (consumer.dep === special) "OK" else "FAIL: @Named on parameter not working"
}
