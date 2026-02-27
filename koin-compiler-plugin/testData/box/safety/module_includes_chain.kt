// FILE: test.kt
import org.koin.dsl.koinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

// Transitive @Module(includes) chain:
// InfraModule -> DataModule -> AppModule
// Each level's definitions should be visible to the next for A2 safety checks.

class Database
class Repository(val db: Database)
class AppService(val repo: Repository)

@Module
class InfraModule {
    @Single
    fun provideDb(): Database = Database()
}

@Module(includes = [InfraModule::class])
class DataModule {
    @Single
    fun provideRepo(db: Database): Repository = Repository(db)
}

@Module(includes = [DataModule::class])
class AppModule {
    @Single
    fun provideService(repo: Repository): AppService = AppService(repo)
}

fun box(): String {
    val koin = koinApplication {
        modules(AppModule().module())
    }.koin

    val service = koin.get<AppService>()
    return if (service.repo.db != null) "OK" else "FAIL"
}
