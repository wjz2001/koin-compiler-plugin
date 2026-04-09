// FILE: test.kt
// FLAKY: This test occasionally fails with "Actual data differs from file content: property_value_ok.fir.ir.txt"
// when run as part of the full ./test.sh suite. Passes deterministically when run alone or with -Pupdate.testdata=true.
// Failure is non-deterministic and not introduced by recent qualifier metadata changes (verified by stashing src and
// re-running baseline — same flake reproduces). Suspected cause: Kotlin compiler test framework state pollution
// within a single JVM (test classes share a JVM under Gradle). Investigate test framework, not this test.
import org.koin.dsl.koinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Property
import org.koin.core.annotation.PropertyValue

// @PropertyValue provides a compile-time default for @Property("key").
// Compile safety should NOT warn because @PropertyValue("api.timeout") exists.
@PropertyValue("api.timeout")
val defaultApiTimeout = 30

@Factory
class ApiClient(@Property("api.timeout") val timeout: Int)

@Module
@ComponentScan
class TestModule

fun box(): String {
    // Verify the module compiles — @Property/@PropertyValue matching is validated at compile time
    val app = koinApplication {
        modules(TestModule().module())
    }
    return "OK"
}
