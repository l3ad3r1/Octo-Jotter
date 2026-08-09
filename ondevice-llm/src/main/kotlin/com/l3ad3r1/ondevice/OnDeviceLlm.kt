package com.l3ad3r1.ondevice

import android.content.Context
import com.arm.aichat.InferenceEngine
import com.arm.aichat.internal.InferenceEngineImpl

/**
 * Public entry point for the on-device LLM runtime.
 *
 * The JNI implementation ([InferenceEngineImpl]) and its `getInstance` factory
 * are `internal` to this module (the native symbols bind to the `com.arm.aichat`
 * package and shouldn't be a public API). This object is the one supported way
 * for `:app` to obtain an [InferenceEngine].
 *
 * Phase 0 (docs/ON-DEVICE-AI.md): prove a GGUF model loads and streams tokens.
 * Higher-level features (RAG chat, completion, filing) will build on top of the
 * returned [InferenceEngine] in later phases.
 */
object OnDeviceLlm {

    /**
     * Obtain the process-wide singleton [InferenceEngine].
     *
     * @throws UnsatisfiedLinkError if the native `ai-chat` library fails to load
     *   (e.g. on a non-arm64 device — callers should device-gate first).
     */
    fun engine(context: Context): InferenceEngine =
        InferenceEngineImpl.getInstance(context.applicationContext)
}
