package com.gettogether.app.jami

/**
 * Android actual for createJamiBridge.
 * Note: This is not actually used at runtime because jamiBridgeModule
 * overrides the JamiBridge binding with SwigJamiBridge.
 * This exists only to satisfy the expect/actual contract for compilation.
 */
actual fun createJamiBridge(): JamiBridge {
    throw IllegalStateException(
        "createJamiBridge() should not be called on Android. " +
        "Use Koin's jamiBridgeModule which provides SwigJamiBridge instead."
    )
}
