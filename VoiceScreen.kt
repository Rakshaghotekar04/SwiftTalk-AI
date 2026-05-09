package com.example.swifttalkai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent

@Composable
fun VoiceScreen() {

    val context = LocalContext.current
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "SwiftTalk AI",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(60.dp))

        FloatingActionButton(
            onClick = {
                if (context is MainActivity) {
                    context.startVoiceRecognition()
                }
            },
            modifier = Modifier.size(100.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Mic",
                modifier = Modifier.size(50.dp)
            )
        }
        IconButton(
            onClick = {
                val intent = Intent(context, CameraActivity::class.java)
                context.startActivity(intent)
            }
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("Tap mic to speak")

        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = "SwiftTalk AI",
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            placeholder = { Text("Type your message...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (context is MainActivity) {
                    context.handleVoiceCommand(message)
                }
            }
        ) {
            Text("Send")
        }
    }
}