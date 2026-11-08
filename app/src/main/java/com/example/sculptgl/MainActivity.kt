package com.example.sculptgl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.sculptgl.ui.SculptGLApp
import com.example.sculptgl.ui.SculptGLViewModel
import com.example.sculptgl.ui.theme.SculptGLTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SculptGLViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SculptGLTheme {
                SculptGLApp(viewModel = viewModel)
            }
        }
    }
}
