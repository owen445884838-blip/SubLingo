package com.sublingo.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sublingo.app.ui.navigation.AppNavHost

@Composable
fun AppShell() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavHost()
        }
    }
}
