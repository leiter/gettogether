package com.gettogether.app

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.gettogether.app.coil.VCardFetcher
import com.gettogether.app.coil.VCardMapper
import com.gettogether.app.di.initKoin
import platform.Foundation.NSLog

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
        setupCoilImageLoader()
    }
) {
    App()
}

/**
 * Configure Coil ImageLoader with VCard fetcher for loading avatars from daemon's vCard files.
 */
private fun setupCoilImageLoader() {
    NSLog("[MainViewController] Setting up Coil ImageLoader with VCardFetcher...")
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(VCardMapper())           // Maps .vcf paths to VCardData
                add(VCardFetcher.Factory())  // Extracts avatars from vCard files
            }
            .build()
    }
    NSLog("[MainViewController] Coil ImageLoader configured")
}
