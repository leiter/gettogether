package com.gettogether.app

import androidx.compose.ui.window.ComposeUIViewController
import com.gettogether.app.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) {
    App()
}
