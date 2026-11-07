package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Numbered(val items: List<String>) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val bulletRegex = Regex("^\\s*[-*\u2022]\\s+")
private val numberedRegex = Regex("^\\s*\\d+[.)]\\s+")
private val inlineRegex = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*\\s][^*]*\\*|`[^`]+`)")

fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.replace("\r", "").split("\n")
    val para = mutableListOf<String>()
    fun flush() {
        if (para.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(para.joinToString("\n").trim()))
            para.clear()
        }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.add(lines[i]); i++
                }
                blocks.add(MdBlock.Code(code.joinToString("\n")))
                i++
            }
            trimmed.isEmpty() -> { flush(); i++ }
            trimmed.startsWith("#") -> {
                flush()
                blocks.add(MdBlock.Heading(trimmed.trimStart('#').trim()))
                i++
            }
            bulletRegex.containsMatchIn(line) -> {
                flush()
                val items = mutableListOf<String>()
                while (i < lines.size && bulletRegex.containsMatchIn(lines[i])) {
                    items.add(lines[i].trim().replaceFirst(bulletRegex, ""))
                    i++
                }
                blocks.add(MdBlock.Bullets(items))
            }
            numberedRegex.containsMatchIn(line) -> {
                flush()
                val items = mutableListOf<String>()
                while (i < lines.size && numberedRegex.containsMatchIn(lines[i])) {
                    items.add(lines[i].trim().replaceFirst(numberedRegex, ""))
                    i++
                }
                blocks.add(MdBlock.Numbered(items))
            }
            else -> { para.add(line); i++ }
        }
    }
    flush()
    return blocks
}

fun inlineMd(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineRegex.findAll(text)) {
        append(text.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(token.drop(2).dropLast(2))
            }
            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                append(token.drop(1).dropLast(1))
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.drop(1).dropLast(1))
            }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in parseMarkdown(text)) {
            when (block) {
                is MdBlock.Paragraph -> Text(
                    inlineMd(block.text),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                is MdBlock.Heading -> Text(
                    inlineMd(block.text),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row {
                            Text("\u2022  ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                            Text(inlineMd(item), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is MdBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { idx, item ->
                        Row {
                            Text((idx + 1).toString() + ".  ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                            Text(inlineMd(item), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is MdBlock.Code -> Surface(
                    color = CodeBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        block.text,
                        color = CodeFg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
