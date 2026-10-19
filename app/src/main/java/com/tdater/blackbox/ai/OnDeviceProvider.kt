package com.tdater.blackbox.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.tdater.blackbox.core.ai.AiPrompt
import com.tdater.blackbox.core.ai.AiProvider
import com.tdater.blackbox.core.ai.AiSuggestion
import com.tdater.blackbox.core.ai.ContextPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs a LiteRT-LM model on the phone's CPU. No network is involved (the app has no INTERNET permission).
 * One engine per request: slower to start, but nothing stays resident in memory between asks.
 * Cancelling asks the model to stop; native resources are released once generation actually ends.
 */
class OnDeviceProvider(private val model: File, private val built: AiPrompt.Built, private val maxTokens: Int = 4096) : AiProvider {
    override val name: String = "on-device model (${model.name})"

    override suspend fun complete(packet: ContextPacket): List<AiSuggestion> = withContext(Dispatchers.Default) {
        val raw = try {
            generate(built.prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Native load failures surface as Errors (e.g. UnsatisfiedLinkError); the gateway only reports Exceptions.
            throw IllegalStateException(t.message ?: t.javaClass.simpleName, t)
        }
        AiPrompt.parse(raw, built.handles)
    }

    private suspend fun generate(prompt: String): String {
        val engine = Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), visionBackend = null,
            audioBackend = null, maxNumTokens = maxTokens, cacheDir = null))
        val released = AtomicBoolean(false)
        var conversation: com.google.ai.edge.litertlm.Conversation? = null
        fun release() {
            if (released.compareAndSet(false, true)) {
                runCatching { conversation?.close() }
                runCatching { engine.close() }
            }
        }
        try {
            engine.initialize()
            val conv = engine.createConversation(ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                systemInstruction = null, tools = emptyList()))
            conversation = conv
        } catch (t: Throwable) {
            release()
            throw t
        }
        return suspendCancellableCoroutine { cont ->
            val out = StringBuilder()
            // Stop the native generation too; onError/onDone then releases the engine.
            cont.invokeOnCancellation { runCatching { conversation?.cancelProcess() } }
            conversation!!.sendMessageAsync(Contents.of(listOf(Content.Text(prompt))), object : MessageCallback {
                override fun onMessage(message: Message) { synchronized(out) { out.append(message.toString()) } }
                override fun onDone() {
                    val text = synchronized(out) { out.toString() }
                    release()
                    if (cont.isActive) cont.resume(text)
                }
                override fun onError(throwable: Throwable) {
                    release()
                    if (cont.isActive) cont.resumeWithException(throwable)
                }
            }, emptyMap())
        }
    }
}
