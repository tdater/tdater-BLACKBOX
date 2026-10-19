package com.tdater.blackbox.core.ai

import com.tdater.blackbox.core.model.PrivacyPolicy
import com.tdater.blackbox.core.model.Project
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/*
 * Optional AI boundary (P1). No provider ships in this build, so every request ends
 * in [AiResult.Unavailable]. The policy gate below is the enforcement point: it runs
 * before any provider is touched, regardless of which UI or code path asked.
 */

/** One selected record sent as context. [projectId] is required so policy can be enforced per record. */
data class ContextItem(val recordId: String, val projectId: String, val kind: String, val text: String)

data class ContextPacket(val task: String, val items: List<ContextItem>) {
    val totalChars: Int get() = items.sumOf { it.text.length }
}

data class AiSuggestion(val text: String, val referencedIds: List<String>)

sealed class AiResult {
    data class Ok(val suggestions: List<AiSuggestion>, val provider: String) : AiResult()
    data class Denied(val reason: String) : AiResult()
    data class Unavailable(val reason: String) : AiResult()
    data class Failed(val reason: String) : AiResult()
}

interface AiProvider {
    val name: String
    suspend fun complete(packet: ContextPacket): List<AiSuggestion>
}

class AiGateway(
    private val provider: AiProvider?,
    private val globalAiEnabled: () -> Boolean,
    private val projectLookup: (String) -> Project?,
    private val maxContextChars: Int = 60_000,
    private val timeoutMs: Long = 45_000,
) {
    /** Pure policy decision; exposed for tests and for disabling UI affordances. */
    fun authorize(packet: ContextPacket): AiResult.Denied? {
        if (!globalAiEnabled()) return AiResult.Denied("AI is disabled globally in Settings.")
        if (packet.items.isEmpty()) return AiResult.Denied("No context selected.")
        val projectIds = packet.items.map { it.projectId }.distinct()
        for (pid in projectIds) {
            val p = projectLookup(pid) ?: return AiResult.Denied("Context references unknown project $pid.")
            if (p.privacyPolicy != PrivacyPolicy.AI_ALLOWED) {
                return AiResult.Denied("Project \"${p.title}\" is LOCAL_ONLY; its records cannot be sent to an AI provider.")
            }
        }
        if (packet.totalChars > maxContextChars) {
            return AiResult.Denied("Selected context is ${packet.totalChars} characters; limit is $maxContextChars. Deselect items.")
        }
        return null
    }

    suspend fun request(packet: ContextPacket): AiResult {
        authorize(packet)?.let { return it }
        val p = provider ?: return AiResult.Unavailable("No AI provider is configured. All offline features remain available.")
        val raw = try {
            withTimeout(timeoutMs) { p.complete(packet) }
        } catch (e: TimeoutCancellationException) {
            return AiResult.Failed("Provider timed out after ${timeoutMs / 1000}s. Nothing was saved.")
        } catch (e: Exception) {
            return AiResult.Failed("Provider error: ${e.javaClass.simpleName}. Nothing was saved.")
        }
        val allowed = packet.items.map { it.recordId }.toSet()
        val invented = raw.flatMap { it.referencedIds }.filter { it !in allowed }
        if (invented.isNotEmpty()) {
            return AiResult.Failed("Response referenced record ids that were not in the selected context (${invented.take(3).joinToString()}). Discarded.")
        }
        return AiResult.Ok(raw, p.name)
    }
}
