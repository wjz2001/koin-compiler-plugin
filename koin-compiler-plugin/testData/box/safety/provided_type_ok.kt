// FILE: test.kt
import org.koin.dsl.koinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Singleton
import org.koin.core.annotation.Provided

// @Provided marks ExternalContext as externally available at runtime.
// Safety validation should skip it (no "Missing dependency" error).
@Provided
class ExternalContext

@Singleton
class Service(val ctx: ExternalContext)

@Module
@ComponentScan
class TestModule

fun box(): String {
    val koin = koinApplication {
        modules(TestModule().module())
    }.koin

    // ExternalContext is @Provided, so Service can be registered even though
    // ExternalContext is not in the DI graph. At runtime it would be provided
    // externally (e.g., Android's Context). For this test, we just verify compilation succeeds.
    // We need to register ExternalContext manually for the runtime check:
    val koin2 = koinApplication {
        modules(
            TestModule().module(),
            org.koin.dsl.module { single { ExternalContext() } }
        )
    }.koin

    val service = koin2.get<Service>()
    return if (service.ctx != null) "OK" else "FAIL"
}
