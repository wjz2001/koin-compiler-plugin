// FILE: test.kt
// FLAKY: Occasionally fails when run as part of the full ./test.sh suite (non-deterministic).
// Passes when run alone. Reproduces on baseline, not introduced by current work. Suspected cause:
// Kotlin compiler test framework state pollution within a shared JVM.
import org.koin.core.annotation.Monitor

// This function is monitored but won't be called directly
// because SDK is not initialized in tests
@Monitor
fun monitoredFunction(): String {
    return "monitored"
}

// Regular function to verify compilation works
fun regularFunction(): String {
    return "regular"
}

fun box(): String {
    // Only call the non-monitored function to verify compilation works
    // The @Monitor annotation transformation is verified via IR golden files
    val regular = regularFunction()
    return if (regular == "regular") "OK" else "FAIL: regular=$regular"
}
