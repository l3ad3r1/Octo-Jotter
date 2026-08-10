package com.l3ad3r1.octojotter.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Runtime device gating for on-device AI (spec §8 of docs/ON-DEVICE-AI.md).
 *
 * The native llama.cpp runtime is built for arm64-v8a only, and model inference
 * is memory-hungry, so features are tiered by ABI and total RAM. On unsupported
 * devices AI is simply hidden and the app behaves exactly as before.
 */
class AiCapability(private val context: Context) {

    private val isArm64: Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    private val totalRamBytes: Long by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
    }

    private val ramGb: Double get() = totalRamBytes / GB.toDouble()

    /** RAM tier the device falls into. */
    val tier: Tier by lazy {
        when {
            !isArm64 || ramGb < 4.0 -> Tier.UNSUPPORTED
            ramGb < 6.0 -> Tier.LIMITED
            else -> Tier.FULL
        }
    }

    /** Any on-device AI at all (semantic search, filing). */
    val supportsAi: Boolean get() = tier != Tier.UNSUPPORTED

    /** Semantic search + indexing. Allowed on LIMITED and FULL. */
    val supportsSemanticSearch: Boolean get() = supportsAi

    /** RAG chat with a small local model. Allowed on LIMITED and FULL. */
    val supportsChat: Boolean get() = supportsAi

    /** Inline completion — the most demanding feature; FULL only. */
    val supportsCompletion: Boolean get() = tier == Tier.FULL

    fun describe(): String =
        "tier=$tier arm64=$isArm64 ram=${"%.1f".format(ramGb)}GB abis=${Build.SUPPORTED_ABIS.joinToString()}"

    enum class Tier { UNSUPPORTED, LIMITED, FULL }

    private companion object {
        const val GB = 1024L * 1024L * 1024L
    }
}
