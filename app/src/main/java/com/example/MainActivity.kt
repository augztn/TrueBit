package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TrueBitApp
import com.example.ui.viewmodel.TrueBitViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as TrueBitApplication).container
        setContent {
            val viewModel: TrueBitViewModel = viewModel(
                factory = TrueBitViewModel.provideFactory(appContainer)
            )
            TrueBitApp(viewModel = viewModel)
        }
    }
}
