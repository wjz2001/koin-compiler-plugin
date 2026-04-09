// FILE: test.kt
// FLAKY: Occasionally fails when run as part of the full ./test.sh suite (non-deterministic).
// Passes when run alone. Reproduces on baseline, not introduced by current work. Suspected cause:
// Kotlin compiler test framework state pollution within a shared JVM.
import org.koin.dsl.koinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Singleton

// Parameter with default value and no provider should NOT trigger a safety error
// (when skipDefaultValues is enabled, which is the default)
@Module
@ComponentScan
class TestModule

@Singleton
class Service(val name: String = "default_name")

fun box(): String {
    val koin = koinApplication {
        modules(TestModule().module())
    }.koin

    val service = koin.get<Service>()

    return if (service.name == "default_name") "OK" else "FAIL: default value not used"
}
