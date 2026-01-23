package com.gettogether.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gettogether.app.ui.theme.GetTogetherTheme
import com.gettogether.app.ui.navigation.AppNavigation
import com.gettogether.app.ui.navigation.InitialNavigation
import com.gettogether.app.ui.util.logPointerEvents
import org.koin.compose.KoinContext

@Composable
fun App(initialNavigation: InitialNavigation? = null) {
    KoinContext {
        GetTogetherTheme {
            AppNavigation(
                modifier = Modifier.logPointerEvents(tag = "GlobalTouch", enabled = true),
                initialNavigation = initialNavigation
            )
        }
    }
}
