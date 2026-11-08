package io.github.timilehin_dev.ogabot.agent

import io.github.timilehin_dev.ogabot.domain.entity.AuditEntry
import io.github.timilehin_dev.ogabot.domain.entity.Message
import io.github.timilehin_dev.ogabot.domain.entity.ToolCall
import io.github.timilehin_dev.ogabot.domain.entity.ToolResult
import io.github.timilehin_dev.ogabot.domain.entity.TurnEvent
import io.github.timilehin_dev.ogabot.domain.entity.TurnOutcome
import io.github.timilehin_dev.ogabot.domain.port.ApprovalGate
import io.github.timilehin_dev.ogabot.domain.port.ApprovalResponder
import io.github.timilehin_dev.ogabot.domain.port.ApprovalResponse
import io.github.timilehin_dev.ogabot.domain.port.AuditSink
import io.github.timilehin_dev.ogabot.domain.port.StreamEvent
import io.github.timilehin_dev.ogabot.domain.port.ToolProvider
import io.github.timilehin_dev.ogabot.domain.port.TurnEngine
import io.github.timilehin_dev.ogabot.domain.port.TurnRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The turn loop. Ours — not vendored, not a framework.
 *
 *  1. assemble  — build the message list (system prompt + history)
 *  2. infer     — stream the model
 *  3. parse     — collect tool calls from the stream
 *  4. dispatch  — resolve the tier, route through the gate
 *  5. execute   — run the tool, audit the result
 *  6. loop      — feed results back, until the model stops calling tools
 *
 * Persistence and cancellation are the caller's responsibility; this engine
 * is pure orchestration and is unit-testable on the JVM.
 */
class AgentTurnEngine(
    private val model: io.github.timilehin_dev.ogabot.domain.port.ModelProvider,
    private val gate: ApprovalGate,
    private val responder: ApprovalResponder,
    private val toolProviders: List<ToolProvider>,
    private val audit: AuditSink,
    private val maxIterations: Int = 8
) : TurnEngine {

    override fun runTurn(request: TurnRequest): Flow<TurnEvent> = flow {
        val descriptors = toolProviders.flatMap { it.descriptors() }
        var history = request.history

        for (iteration in 0 until maxIterations) {
            val calls = mutableListOf<ToolCall>()
            var streamError: String? = null

            model.stream(history, descriptors).collect { ev ->
                when (ev) {
                    is StreamEvent.Delta -> emit(TurnEvent.Delta(ev.text))
                    is StreamEvent.ToolCallEvent -> calls.add(ev.call)
                    is StreamEvent.Error -> streamError = ev.message
                    StreamEvent.Done -> {}
                }
            }

            if (streamError != null) {
                emit(TurnEvent.Failed(streamError))
                return@flow
            }

            if (calls.isEmpty()) {
                emit(TurnEvent.Completed(TurnOutcome.COMPLETED))
                return@flow
            }

            val results = mutableListOf<ToolResult>()
            for (call in calls) {
                val provider = toolProviders.firstOrNull { p ->
                    p.descriptors().any { it.name == call.name }
                }
                if (provider == null) {
                    results += ToolResult(call.id, call.name, false, "Unknown tool: ${call.name}")
                    continue
                }

                val decision = gate.decide(request.bot.id, call.name, call.arguments, request.background)
                when (decision) {
                    is ApprovalGate.Decision.Allow -> {
                        val r = provider.executor().execute(call)
                        audit.record(r.toAudit(request.bot.id))
                        results += r
                    }
                    ApprovalGate.Decision.Deny -> {
                        results += ToolResult(
                            call.id, call.name, false,
                            "Refused: this tool is set to never-allow for you. Do not retry it."
                        )
                    }
                    is ApprovalGate.Decision.Ask -> {
                        val approval = io.github.timilehin_dev.ogabot.domain.entity.ApprovalRequest(
                            id = "ar_" + System.currentTimeMillis().toString(36) + "_" + call.id,
                            botId = request.bot.id,
                            tool = call.name,
                            arguments = call.arguments,
                            tier = "ask",
                            background = request.background,
                            createdAt = System.currentTimeMillis()
                        )
                        emit(TurnEvent.NeedsApproval(approval))
                        when (responder.request(approval)) {
                            ApprovalResponse.ALLOW_ONCE -> {
                                val r = provider.executor().execute(call)
                                audit.record(r.toAudit(request.bot.id))
                                results += r
                            }
                            ApprovalResponse.ALLOW_ALWAYS -> {
                                gate.addRule("allow ${call.name}", askFirst = false)
                                val r = provider.executor().execute(call)
                                audit.record(r.toAudit(request.bot.id))
                                results += r
                            }
                            ApprovalResponse.DENY -> {
                                results += ToolResult(
                                    call.id, call.name, false,
                                    "The user denied this action. Do not attempt it again for this request."
                                )
                            }
                        }
                    }
                }
            }

            // Feed the results back as a user message and loop.
            history = history + Message(
                role = "user",
                content = "[Tool results]\n" + results.joinToString("\n") { r ->
                    "(" + r.tool + ") " + (if (r.ok) "OK: " else "ERROR: ") + r.output
                }
            )
        }

        emit(TurnEvent.Completed(TurnOutcome.COMPLETED))
    }
}

private fun ToolResult.toAudit(botId: String) = AuditEntry(
    botId = botId,
    tool = tool,
    ok = ok,
    output = output,
    durationMs = durationMs,
    tier = tier
)
