package com.l3ad3r1.octojotter.ai.graph

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.l3ad3r1.octojotter.ai.AiContainer
import com.l3ad3r1.octojotter.ai.chat.Citation
import com.l3ad3r1.octojotter.ai.chat.RagEvent
import com.l3ad3r1.octojotter.ai.index.SimilarNotePair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AI augmentation for Graph View, layered on the on-device embedder and chat
 * model already wired for search/RAG ([AiContainer]) — the app's own version of
 * what Kwipu (github.com/benmaster82/Kwipu) does with a local Graph-RAG server,
 * done fully on-device instead: semantic edges from vector similarity, an
 * on-demand relationship label between two connected notes, and a grounded
 * question over the graph.
 *
 * Kept out of [com.l3ad3r1.octojotter.ui.NoteViewModel] the same way
 * [com.l3ad3r1.octojotter.ai.chat.AiChatViewModel] and
 * [com.l3ad3r1.octojotter.ai.settings.AiSettingsViewModel] are: an AI feature
 * that's optional on the device shouldn't bloat the always-present ViewModel.
 */
class GraphAiViewModel(application: Application) : AndroidViewModel(application) {

    private val ai = AiContainer.get(application)

    // --- Semantic edges ---

    /** False when embeddings would come from the hashing fallback (no model
     *  downloaded yet) — that isn't real semantic similarity, so it must not be
     *  drawn as if it were (see EmbeddingService's fallback caveat). */
    private val semanticEdgesSupported: Boolean
        get() = ai.capability.supportsSemanticSearch && ai.useRealEmbedder

    suspend fun semanticLinks(): List<SimilarNotePair> {
        if (!semanticEdgesSupported) return emptyList()
        return ai.noteSimilarity().topPairs(modelId = ai.embedder().modelId)
    }

    // --- On-demand relation labeling ---

    /** One short on-device-LLM-generated description of how two notes relate. */
    sealed interface RelationLabel {
        data object Loading : RelationLabel
        data class Ready(val text: String) : RelationLabel
        data class Failed(val message: String) : RelationLabel
    }

    data class RelationNote(val id: Int, val title: String, val content: String)

    val relationLabelingReady: Boolean
        get() = ai.capability.supportsChat && ai.isChatModelReady()

    private val _relationLabels = MutableStateFlow<Map<Pair<Int, Int>, RelationLabel>>(emptyMap())
    val relationLabels: StateFlow<Map<Pair<Int, Int>, RelationLabel>> = _relationLabels.asStateFlow()

    /**
     * Ask the on-device model, in one short phrase, how [a] and [b] relate.
     * Cached per pair for the life of this screen visit — a second tap on the
     * same edge is free.
     */
    fun labelRelation(a: RelationNote, b: RelationNote) {
        val key = relationKey(a.id, b.id)
        if (_relationLabels.value.containsKey(key)) return
        if (!relationLabelingReady) {
            setLabel(key, RelationLabel.Failed("Download the chat model in AI settings to label relationships."))
            return
        }
        setLabel(key, RelationLabel.Loading)
        viewModelScope.launch {
            val result = runCatching {
                val text = StringBuilder()
                ai.textGenerator()
                    .generate(RELATION_SYSTEM_PROMPT, relationPrompt(a, b), maxTokens = RELATION_MAX_TOKENS)
                    .collect { text.append(it) }
                text.toString().trim().ifBlank { "No clear relation found." }
            }
            setLabel(
                key,
                result.fold(
                    onSuccess = { RelationLabel.Ready(it) },
                    onFailure = { RelationLabel.Failed(it.message ?: "Couldn't generate a label.") },
                ),
            )
        }
    }

    private fun setLabel(key: Pair<Int, Int>, value: RelationLabel) {
        _relationLabels.value = _relationLabels.value + (key to value)
    }

    private fun relationPrompt(a: RelationNote, b: RelationNote) = buildString {
        appendLine("Note \"${a.title}\":")
        appendLine(a.content.take(RELATION_CONTENT_CHARS))
        appendLine()
        appendLine("Note \"${b.title}\":")
        append(b.content.take(RELATION_CONTENT_CHARS))
    }

    // --- Grounded query over the graph ---

    sealed interface QueryState {
        data object Idle : QueryState
        data class Answering(val text: String, val citations: List<Citation>) : QueryState
        data class Done(val text: String, val citations: List<Citation>) : QueryState
        data class Error(val message: String) : QueryState
    }

    val queryReady: Boolean
        get() = ai.capability.supportsChat && ai.isChatModelReady()

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState.asStateFlow()

    /** Ask a question grounded in the user's notes; citations drive which graph
     *  nodes get highlighted (see GraphViewScreen). */
    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        if (!queryReady) {
            _queryState.value = QueryState.Error("Download the chat model in AI settings to ask questions.")
            return
        }
        viewModelScope.launch {
            val answer = StringBuilder()
            var citations: List<Citation> = emptyList()
            ai.ragChat().ask(q).collect { event ->
                when (event) {
                    is RagEvent.Sources -> {
                        citations = event.citations
                        _queryState.value = QueryState.Answering(answer.toString(), citations)
                    }
                    is RagEvent.Token -> {
                        answer.append(event.text)
                        _queryState.value = QueryState.Answering(answer.toString(), citations)
                    }
                    is RagEvent.Error -> _queryState.value = QueryState.Error(event.message)
                    RagEvent.Done -> _queryState.value = QueryState.Done(answer.toString(), citations)
                }
            }
        }
    }

    fun clearQuery() {
        _queryState.value = QueryState.Idle
    }

    companion object {
        /** Normalizes a note-id pair so (a, b) and (b, a) share one cache entry. */
        fun relationKey(a: Int, b: Int): Pair<Int, Int> = if (a < b) a to b else b to a

        private const val RELATION_MAX_TOKENS = 24
        private const val RELATION_CONTENT_CHARS = 600
        private const val RELATION_SYSTEM_PROMPT =
            "In under 12 words, describe how these two notes relate to each other. " +
                "Answer with a single short phrase only — no preamble, no closing punctuation."
    }
}
