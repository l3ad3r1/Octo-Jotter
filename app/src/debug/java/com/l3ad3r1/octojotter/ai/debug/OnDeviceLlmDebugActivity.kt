package com.l3ad3r1.octojotter.ai.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.arm.aichat.InferenceEngine
import com.l3ad3r1.octojotter.ui.theme.MyApplicationTheme
import com.l3ad3r1.ondevice.OnDeviceLlm
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.io.File

/**
 * Phase 0 smoke test for on-device inference (docs/ON-DEVICE-AI.md).
 *
 * DEBUG BUILDS ONLY — this activity lives in the `debug` source set and is never
 * compiled into a release APK. It proves the native llama.cpp runtime loads a
 * GGUF model and streams tokens end-to-end, with no note data involved.
 *
 * Usage:
 *   1. Push a small GGUF onto the device at the path shown in the screen, e.g.
 *      adb push model.gguf /sdcard/Android/data/com.l3ad3r1.octojotter/files/models/model.gguf
 *   2. Launch "OctoJotter LLM Debug" from the launcher (debug build).
 *   3. Load → enter a prompt → Generate. Tokens should stream in.
 */
class OnDeviceLlmDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Scaffold { padding ->
                    OnDeviceLlmDebugScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun OnDeviceLlmDebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Default location the user can `adb push` a model to.
    val defaultModelPath = remember {
        File(context.getExternalFilesDir(null), "models/model.gguf").absolutePath
    }
    var modelPath by remember { mutableStateOf(defaultModelPath) }
    var prompt by remember { mutableStateOf("Write one sentence about octopuses.") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("idle") }

    val isArm64 = remember { Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } }

    // Lazily created so a non-arm64 device doesn't crash on the UnsatisfiedLinkError
    // until the user actually asks for inference.
    val engine: InferenceEngine? = remember {
        if (isArm64) runCatching { OnDeviceLlm.engine(context) }.getOrNull() else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("On-Device LLM — Phase 0 smoke test", style = MaterialTheme.typography.titleMedium)
        Text("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}", style = MaterialTheme.typography.bodySmall)
        if (engine != null) {
            val engineState by engine.state.collectAsState()
            Text("Engine state: $engineState", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Engine state: n/a", style = MaterialTheme.typography.bodySmall)
        }
        Text("Status: $status", style = MaterialTheme.typography.bodySmall)

        if (!isArm64 || engine == null) {
            Text(
                "On-device inference is unavailable on this device/emulator " +
                    "(requires arm64-v8a). This is expected on x86 emulators.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedTextField(
            value = modelPath,
            onValueChange = { modelPath = it },
            label = { Text("GGUF model path") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            enabled = engine != null,
            onClick = {
                val e = engine ?: return@Button
                scope.launch {
                    status = "loading model…"
                    output = ""
                    runCatching {
                        if (!File(modelPath).exists()) error("Model file not found at $modelPath")
                        e.loadModel(modelPath)
                    }.onSuccess { status = "model loaded" }
                        .onFailure { status = "load failed: ${it.message}" }
                }
            },
        ) { Text("Load model") }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            enabled = engine != null,
            onClick = {
                val e = engine ?: return@Button
                scope.launch {
                    status = "generating…"
                    output = ""
                    e.sendUserPrompt(prompt)
                        .catch { status = "generation error: ${it.message}" }
                        .onCompletion { if (status == "generating…") status = "done" }
                        .collect { token -> output += token }
                }
            },
        ) { Text("Generate") }

        Text(
            text = output.ifBlank { "(output appears here)" },
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}
