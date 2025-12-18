package com.gettogether.app

import androidx.compose.runtime.Composable
import com.gettogether.app.ui.theme.GetTogetherTheme
import com.gettogether.app.ui.navigation.AppNavigation
import org.koin.compose.KoinContext

@Composable
fun App() {
    KoinContext {
        GetTogetherTheme {
            AppNavigation()
        }
    }
}
