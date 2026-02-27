// RUN_PIPELINE_TILL: BACKEND
// FILE: test.kt
import org.koin.core.annotation.Module
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Singleton

// ExternalContext is NOT @Provided → safety validation should report missing dependency
class ExternalContext

@Singleton
class Service(val ctx: ExternalContext)

@Module
@ComponentScan
class TestModule

/* GENERATED_FIR_TAGS: classDeclaration, primaryConstructor, propertyDeclaration */
