package examples.klib

import org.koin.core.annotation.*

/**
 * Test module for KLIB-based targets (JS, WasmJs, Native).
 * Verifies that the compiler plugin correctly skips FIR-generated declarations
 * on these platforms to avoid KT-82395 serialization crashes.
 */

interface KlibService

@Singleton
class KlibServiceImpl : KlibService

@Factory
class KlibClient(val service: KlibService)

@Module
@ComponentScan("examples.klib")
class KlibTestModule
