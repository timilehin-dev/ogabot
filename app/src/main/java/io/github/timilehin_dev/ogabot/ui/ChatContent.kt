package io.github.timilehin_dev.ogabot.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.timilehin_dev.ogabot.AppGraph
import io.github.timilehin_dev.ogabot.ChatController
import io.github.timilehin_dev.ogabot.domain.entity.Bot

/**
 * The chat tab of the bot detail. Streaming answers, tool-call status lines,
 * inline approval cards, and a pill composer.
 */
@Composable
fun ChatContent(
    graph: AppGraph,
    bot: Bot,
    controller: ChatController,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(controller.messages.size, controller.streaming) {
        if (controller.messages.isNotEmpty()) {
            listState.animateScrollToItem(controller.messages.size - 1)
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (controller.messages.isEmpty() && !controller.streaming) {
                item { EmptyChat(bot.name, bot.colorIndex) }
            }
            items(
                controller.messages,
                key = { it.timestamp.toString() + it.role + it.content.take(24) }
            ) { m ->
                if (m.role == "user") UserBubble(m.content) else AssistantBubble(m.content)
            }
            if (controller.streaming) {
                item { TypingDots() }
            }
        }

        if (controller.toolLines.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                controller.toolLines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        controller.pendingApproval?.let { req ->
            ApprovalCard(
                request = req,
                onAllowOnce = { controller.respond(io.github.timilehin_dev.ogabot.domain.port.ApprovalResponse.ALLOW_ONCE) },
                onAlways = { controller.respond(io.github.timilehin_dev.ogabot.domain.port.ApprovalResponse.ALLOW_ALWAYS) },
                onDeny = { controller.respond(io.github.timilehin_dev.ogabot.domain.port.ApprovalResponse.DENY) }
            )
        }

        controller.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        err,
                        Modifier.weight(1f).padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { controller.retry() }) { Text("Retry") }
                    TextButton(onClick = { controller.dismissError() }) { Text("✕") }
                }
            }
        }

        PillComposer(
            value = input,
            onValueChange = { input = it },
            hint = "Message ${bot.name}…",
            enabled = !controller.streaming,
            onSend = {
                controller.send(input)
                input = ""
            }
        )
    }
}

@Composable
fun AssistantBubble(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(28.dp).background(AvatarBrush, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("O", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            MarkdownText(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 4.dp, bottomEnd = 24.dp, bottomStart = 24.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun TypingDots() {
    Row(
        Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 4.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { n ->
            val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(650),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(n * 220)
                ),
                label = "dot$n"
            )
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer { this.alpha = a }
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun ApprovalCard(
    request: io.github.timilehin_dev.ogabot.domain.entity.ApprovalRequest,
    onAllowOnce: () -> Unit,
    onAlways: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Approve ${request.tool}?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                request.arguments.take(220),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (request.background) {
                Text(
                    "Requested by a background task — expires in 30 min if unanswered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = onAllowOnce, shape = RoundedCornerShape(20.dp)) { Text("Allow once") }
                androidx.compose.material3.Button(onClick = onAlways, shape = RoundedCornerShape(20.dp)) { Text("Always allow") }
                TextButton(onClick = onDeny) { Text("Deny", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun PillComposer(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Surface(color = Bg) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(26.dp),
                maxLines = 4,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(10.dp))
            val canSend = enabled && value.isNotBlank()
            Box(
                Modifier
                    .size(52.dp)
                    .background(
                        if (canSend) AvatarBrush
                        else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant),
                        androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.IconButton(onClick = onSend, enabled = canSend) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(botName: String, colorIndex: Int) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).background(avatarBrushFor(colorIndex), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(botName.take(1).uppercase().ifEmpty { "B" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("$botName is ready", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ask anything — replies stream live.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
