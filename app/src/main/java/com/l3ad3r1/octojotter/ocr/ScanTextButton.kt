package com.l3ad3r1.octojotter.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Captures a photo with the system camera app and runs on-device text
 * recognition (ML Kit's bundled Latin model — no network, no Play Services
 * download) on it, handing the extracted text to [onTextRecognized].
 *
 * A plain composable function rather than a button: it owns the permission
 * request + launcher + recognizer, and calls [content] with the click handler
 * and a busy flag so the caller can render whatever button/icon fits (the
 * editor toolbar's existing icon-button style, in practice).
 */
@Composable
fun ScanTextAction(
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit,
    content: @Composable (onClick: () -> Unit, busy: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    fun runRecognition(uri: Uri) {
        busy = true
        scope.launch {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = suspendCancellableCoroutine<Text> { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                val text = result.text.trim()
                if (text.isEmpty()) {
                    onError("No text found in that photo.")
                } else {
                    onTextRecognized(text)
                }
            } catch (t: Throwable) {
                onError(t.message ?: "Text recognition failed.")
            } finally {
                busy = false
            }
        }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingUri
        if (success && uri != null) runRecognition(uri) else busy = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = newCaptureUri(context)
            pendingUri = uri
            captureLauncher.launch(uri)
        } else {
            onError("Camera permission is needed to scan text.")
        }
    }

    val onClick: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = newCaptureUri(context)
            pendingUri = uri
            captureLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    content(onClick, busy)
}

private fun newCaptureUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
    // Each scan wrote a full-size JPEG here and nothing ever removed it, so the
    // cache grew by a photo per scan for the life of the install. The captured
    // image is only needed until its text has been recognised, so clear out
    // previous captures whenever a new one starts.
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
