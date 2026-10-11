package com.advanceai.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.advanceai.calculator.ui.AppRoot
import com.advanceai.calculator.ui.CalculatorViewModel
import com.advanceai.calculator.ui.theme.MCalculatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MCalculatorTheme {
                val viewModel: CalculatorViewModel = viewModel()
                AppRoot(viewModel = viewModel)
            }
        }
    }
}
