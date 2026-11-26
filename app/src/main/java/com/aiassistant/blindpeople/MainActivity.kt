
package com.aiassistant.blindpeople

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import androidx.core.content.ContextCompat

import com.aiassistant.blindpeople.ui.theme.AIAssistantForBlindPeopleTheme

import java.util.Locale

/**
 * Main entry point for the AI Assistant for Blind People.
 *
 * The previous artificial-image depth inference test
 * has been removed from the startup process.
 *
 * Camera-based automatic depth estimation is now
 * handled by CameraScreen.
 *
 * No user-entered height, distance, or calibration
 * is required for automatic depth estimation.
 */
class MainActivity : ComponentActivity() {

    private var textToSpeech: TextToSpeech? = null

    /**
     * Speaks a message using Android Text-to-Speech.
     */
    private fun speak(message: String) {

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "assistant_message"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Initialize Text-to-Speech.
        textToSpeech = TextToSpeech(this) { status ->

            if (status == TextToSpeech.SUCCESS) {

                val result = textToSpeech?.setLanguage(
                    Locale.ENGLISH
                )

                if (
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                ) {

                    speak("AI Vision Assistant started.")
                }
            }
        }

        /*
         * IMPORTANT:
         *
         * The old runDepthInferenceTest() call is removed.
         *
         * We no longer run the artificial gray-image
         * inference test when the application starts.
         *
         * CameraScreen is responsible for obtaining
         * camera frames and running depth estimation.
         */

        setContent {

            AIAssistantForBlindPeopleTheme {

                AssistantScreen(
                    onSpeak = { message ->

                        speak(message)
                    }
                )
            }
        }
    }

    override fun onDestroy() {

        // Stop and release Text-to-Speech.
        textToSpeech?.stop()
        textToSpeech?.shutdown()

        textToSpeech = null

        super.onDestroy()
    }
}

/**
 * Main application screen.
 *
 * Preserves:
 * - Camera permission handling
 * - Microphone permission handling
 * - CameraScreen
 * - YOLO11n detection functionality
 * - Voice recognition
 * - Text-to-Speech callback
 *
 * Does not perform the previous artificial-image
 * depth inference test.
 */
@Composable
fun AssistantScreen(
    onSpeak: (String) -> Unit
) {

    val context = LocalContext.current

    // -------------------------------------------------
    // CAMERA PERMISSION
    // -------------------------------------------------

    var hasCameraPermission by remember {

        mutableStateOf(

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // -------------------------------------------------
    // MICROPHONE PERMISSION
    // -------------------------------------------------

    var hasMicrophonePermission by remember {

        mutableStateOf(

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // -------------------------------------------------
    // VOICE RECOGNITION STATE
    // -------------------------------------------------

    var recognizedCommand by remember {

        mutableStateOf(
            "No voice command received yet"
        )
    }

    var recognitionStatus by remember {

        mutableStateOf(
            "Voice recognition is being prepared"
        )
    }

    // -------------------------------------------------
    // VOICE RECOGNITION MANAGER
    // -------------------------------------------------

    val voiceManager = remember {

        VoiceRecognitionManager(

            context = context,

            onResult = { command ->

                recognizedCommand = command

                recognitionStatus =
                    "Command recognized"

                onSpeak(
                    "I heard: $command"
                )
            },

            onError = { error ->

                recognitionStatus = error
            }
        )
    }

    // Release voice recognition resources.
    DisposableEffect(voiceManager) {

        onDispose {

            voiceManager.destroy()
        }
    }

    // -------------------------------------------------
    // PERMISSION LAUNCHERS
    // -------------------------------------------------

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(

            contract =
                ActivityResultContracts.RequestPermission()

        ) { isGranted ->

            hasCameraPermission = isGranted
        }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(

            contract =
                ActivityResultContracts.RequestPermission()

        ) { isGranted ->

            hasMicrophonePermission = isGranted

            recognitionStatus = if (isGranted) {

                "Microphone ready"

            } else {

                "Microphone permission denied"
            }
        }

    // Request the necessary permissions.
    LaunchedEffect(Unit) {

        if (!hasCameraPermission) {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )

        } else if (!hasMicrophonePermission) {

            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    LaunchedEffect(hasCameraPermission) {

        if (
            hasCameraPermission &&
            !hasMicrophonePermission
        ) {

            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    // -------------------------------------------------
    // MAIN APPLICATION UI
    // -------------------------------------------------

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // ---------------------------------------------
        // CAMERA AND AI MODELS
        // ---------------------------------------------

        if (hasCameraPermission) {

            /*
             * CameraScreen is responsible for:
             *
             * - CameraX frames
             * - YOLO11n object detection
             * - Spatial detection
             * - Depth Anything V2 inference
             * - Automatic object-distance fusion
             *
             * No manual calibration is performed here.
             */

            CameraScreen(
                modifier = Modifier.fillMaxSize()
            )

        } else {

            Column(

                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center

            ) {

                Text(
                    text = "Camera permission required",
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Button(

                    onClick = {

                        cameraPermissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }

                ) {

                    Text("Allow Camera")
                }
            }
        }

        // ---------------------------------------------
        // VOICE COMMAND PANEL
        // ---------------------------------------------

        Surface(

            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),

            color = MaterialTheme.colorScheme.surface.copy(
                alpha = 0.95f
            )

        ) {

            Column(

                modifier = Modifier.padding(16.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally

            ) {

                // Developer-only informational status.
                // Actual camera-depth status belongs
                // to CameraScreen.
                Text(

                    text = "Automatic depth mode",

                    style =
                        MaterialTheme.typography.bodyMedium,

                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(

                    text = recognitionStatus,

                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(

                    text = recognizedCommand,

                    style =
                        MaterialTheme.typography.bodyLarge,

                    textAlign = TextAlign.Center,

                    modifier = Modifier.semantics {

                        contentDescription =
                            "Recognized command: " +
                                    recognizedCommand
                    }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Button(

                    onClick = {

                        if (hasMicrophonePermission) {

                            recognitionStatus = "Listening..."

                            voiceManager.startListening()

                        } else {

                            microphonePermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    }

                ) {

                    Text("Start Voice Command")
                }
            }
        }
    }
}
