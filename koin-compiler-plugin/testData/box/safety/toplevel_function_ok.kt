// FILE: test.kt
import org.koin.dsl.koinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Singleton

// Top-level @Singleton function provides DataSource.
// Safety validation should see DataSource as a provided type.
interface DataSource
class DatabaseDataSource : DataSource

@Singleton
fun provideDataSource(): DataSource = DatabaseDataSource()

@Singleton
class Service(val ds: DataSource)

@Module
@ComponentScan
class TestModule

fun box(): String {
    val koin = koinApplication {
        modules(TestModule().module())
    }.koin

    val service = koin.get<Service>()
    val ds = koin.get<DataSource>()

    return if (service.ds === ds && ds is DatabaseDataSource) "OK" else "FAIL"
}
