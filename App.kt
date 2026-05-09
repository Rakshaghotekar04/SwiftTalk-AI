package com.example.swifttalkai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.compose.ui.platform.LocalContext
import android.app.Activity


@Composable
fun App(viewModel: VoiceViewModel = viewModel()) {

    val openCamera by viewModel.openCamera.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(openCamera) {

        if (openCamera) {

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.CAMERA),
                    100
                )
            }
        }
    }
    val isListening by viewModel.isListening.collectAsState()
    val text by viewModel.spokenText.collectAsState()

    var userInput by remember { mutableStateOf("") }

    MaterialTheme {

        // ---------- CAMERA MODE ----------
        if (openCamera) {

            CameraScreen()

        }

        // ---------- NORMAL AI SCREEN ----------
        else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    "SwiftTalk AI",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(40.dp))

                // MIC BUTTON
                Button(
                    onClick = { viewModel.startListening() },
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (isListening) Color.Red
                            else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Mic")
                }

                // CAMERA ICON
                IconButton(
                    onClick = { viewModel.openCameraManually() }
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                // TEXT INPUT
                TextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    placeholder = { Text("Type your message...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (userInput.isNotBlank()) {
                            viewModel.sendText(userInput)
                            userInput = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send")
                }
            }
        }
    }
}