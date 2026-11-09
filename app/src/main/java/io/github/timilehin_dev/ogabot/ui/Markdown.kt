package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A small, deliberate markdown renderer — enough for bot answers:
 * headings, bullets, numbered lists, code spans, bold, italic, and paragraphs.
 * No HTML, no external dependency.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Text(
                    block.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                is Block.Bullet -> Text(
                    inline("•  " + block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
                is Block.Numbered -> Text(
                    inline(block.index.toString() + ".  " + block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
                is Block.Code -> Text(
                    block.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = CodeFg,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                is Block.Paragraph -> Text(
                    inline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

private sealed interface Block {
    data class Heading(val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Numbered(val index: Int, val text: String) : Block
    data class Code(val text: String) : Block
    data class Paragraph(val text: String) : Block
}

private val numberedRe = Regex("^(\\d+)\\.\\s+(.*)$")

private fun parseBlocks(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = text.lines()
    var i = 0
    var paragraph = StringBuilder()

    fun flushParagraph() {
        val p = paragraph.toString().trim()
        if (p.isNotEmpty()) blocks += Block.Paragraph(p)
        paragraph = StringBuilder()
    }

    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("```") -> {
                flushParagraph()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                i++
                blocks += Block.Code(code.toString().trimEnd())
            }
            line.startsWith("# ") -> { flushParagraph(); blocks += Block.Heading(line.removePrefix("# ").trim()); i++ }
            line.startsWith("## ") -> { flushParagraph(); blocks += Block.Heading(line.removePrefix("## ").trim()); i++ }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph(); blocks += Block.Bullet(line.substring(2).trim()); i++
            }
            numberedRe.matches(line) -> {
                flushParagraph()
                val m = numberedRe.find(line)!!
                blocks += Block.Numbered(m.groupValues[1].toInt(), m.groupValues[2].trim()); i++
            }
            line.isBlank() -> { flushParagraph(); i++ }
            else -> { paragraph.appendLine(line.trim()); i++ }
        }
    }
    flushParagraph()
    return blocks
}

/** Inline formatting: **bold**, *italic*, `code`. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    append(text.substring(i + 2, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), length - (end - i - 2), length)
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) {
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Medium), length - (end - i - 1), length)
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace), length - (end - i - 1), length)
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
