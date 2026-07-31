package com.helper.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Лёгкий markdown-рендерер ответов ассистента без сторонних библиотек.
 *
 * Поддержка:
 *  - Блочное: заголовки (# ## ###), цитаты (>), маркированные списки (-, *, +),
 *    нумерованные (1.), кодовые блоки (```), пустые строки как отступы.
 *  - Инлайн: **bold**, __bold__, *italic*, _italic_, `code`, ~~strike~~.
 *
 * Парсер блочных элементов использует явные проверки префикса (без пересекающихся
 * регулярок), инлайн — детерминированный однонаправленный обход с буфером.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = parseBlocks(markdown)
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Code -> CodeBlock(block.content, baseColor)
                is Block.Quote -> QuoteBlock(block.content, baseColor)
                is Block.Heading -> HeadingBlock(block.level, block.text, baseColor)
                is Block.ListItem -> BulletRow(block.marker, block.text, baseColor)
                is Block.Paragraph -> Text(
                    text = inline(block.text, baseColor),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Блочные элементы
// ──────────────────────────────────────────────────────────────────────

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Quote(val content: String) : Block
    data class Code(val content: String) : Block
    data class ListItem(val marker: String, val text: String) : Block
}

/** Разбор текста на блоки по строкам. Кодовые блоки (```) захватывают сырой текст. */
private fun parseBlocks(text: String): List<Block> {
    val out = ArrayList<Block>()
    val lines = text.replace("\r\n", "\n").split("\n")
    var i = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            out.add(Block.Paragraph(paragraph.toString().trim()))
            paragraph.clear()
        }
    }

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()

        // Кодовый блок ``` ... ```
        if (line.trim().startsWith("```")) {
            flushParagraph()
            val buf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                buf.append(lines[i]).append('\n')
                i++
            }
            out.add(Block.Code(buf.toString().trimEnd('\n')))
            i++ // пропустить закрывающий ```
            continue
        }

        if (line.isBlank()) {
            flushParagraph()
            i++
            continue
        }

        // Цитата >
        val quoteMatch = line.trim().let { if (it.startsWith(">")) it.removePrefix(">").trim() else null }
        if (quoteMatch != null) {
            flushParagraph()
            out.add(Block.Quote(quoteMatch))
            i++
            continue
        }

        // Заголовки # / ## / ###
        val heading = parseHeading(line)
        if (heading != null) {
            flushParagraph()
            out.add(Block.Heading(heading.first, heading.second))
            i++
            continue
        }

        // Маркированный список: -, *, + (ровно один маркер + пробел)
        val bullet = parseBullet(line)
        if (bullet != null) {
            flushParagraph()
            out.add(Block.ListItem("•", bullet))
            i++
            continue
        }

        // Нумерованный список: 1. / 12)
        val ordered = parseOrdered(line)
        if (ordered != null) {
            flushParagraph()
            out.add(Block.ListItem(ordered.first + ".", ordered.second))
            i++
            continue
        }

        // Обычный абзац: накапливаем (склеиваем переносы внутри абзаца пробелом,
        // как это делает markdown).
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(line.trim())
        i++
    }
    flushParagraph()
    return out
}

private fun parseHeading(line: String): Pair<Int, String>? {
    val m = Regex("""^(#{1,6})\s+(.+)$""").find(line.trim()) ?: return null
    return m.groupValues[1].length to m.groupValues[2].trim()
}

/** Маркированный элемент: требует '- ', '* ' или '+ ' в начале. Возвращает содержимое. */
private fun parseBullet(line: String): String? {
    val m = Regex("""^([-*+])\s+(.+)""").find(line.trim()) ?: return null
    val content = m.groupValues[2]
    // Гарантия, что это список, а не «жирный с переносом»: содержимое не пустое.
    return content.ifBlank { null }
}

/** Нумерованный элемент: «1. текст» или «1) текст». Возвращает (номер, содержимое). */
private fun parseOrdered(line: String): Pair<String, String>? {
    val m = Regex("""^(\d{1,3})[.)]\s+(.+)""").find(line.trim()) ?: return null
    return m.groupValues[1] to m.groupValues[2]
}

// ──────────────────────────────────────────────────────────────────────
// Рендер блоков
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun HeadingBlock(level: Int, text: String, color: Color) {
    val base = MaterialTheme.typography.bodyMedium
    val style = when (level) {
        1 -> base.copy(fontWeight = FontWeight.Bold, fontSize = base.fontSize * 1.2f)
        2 -> base.copy(fontWeight = FontWeight.Bold, fontSize = base.fontSize * 1.1f)
        else -> base.copy(fontWeight = FontWeight.SemiBold, fontSize = base.fontSize * 1.0f)
    }
    Text(text = inline(text, color), style = style)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun QuoteBlock(content: String, color: Color) {
    Row {
        // Левая цветная полоса — единственный допустимый «accent border», т.к.
        // она несёт смысловую нагрузку (маркирует цитату), а не декор.
        Text(
            text = "▎",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = inline(content, color.copy(alpha = 0.85f)),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun CodeBlock(content: String, color: Color) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        color = color.copy(alpha = 0.9f),
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun BulletRow(marker: String, content: String, color: Color) {
    // Буллит «•» — с отступом в два пробела; нумерованный «1.» — один пробел.
    val label = if (marker == "•") "$marker  " else "$marker "
    Row {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = inline(content, color),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
// Инлайн-разметка (однонаправленный обход, без мутаций индексов)
// ──────────────────────────────────────────────────────────────────────

/**
 * Порядок проверок важен: сначала двухсимвольные маркеры (** __ ~~ ``),
 * затем односимвольные (* _). Каждый маркер ищет парный и, если найден,
 * применяет стиль к содержимому; иначе символ добавляется как обычный текст.
 */
private fun inline(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val strike = SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
    val code = SpanStyle(fontFamily = FontFamily.Monospace)

    val buf = StringBuilder()
    fun flush() { if (buf.isNotEmpty()) { append(buf.toString()); buf.clear() } }

    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]
        val two = if (i + 1 < n) text[i].toString() + text[i + 1] else ""

        when {
            two == "**" || two == "__" -> {
                val end = findPair(text, two, i + 2)
                if (end > 0) { flush(); withStyle(bold) { append(text, i + 2, end) }; i = end + 2; continue }
            }
            two == "~~" -> {
                val end = findPair(text, "~~", i + 2)
                if (end > 0) { flush(); withStyle(strike) { append(text, i + 2, end) }; i = end + 2; continue }
            }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) { flush(); withStyle(code) { append(text, i + 1, end) }; i = end + 1; continue }
            }
            c == '*' || c == '_' -> {
                // Одинарный курсив: ищем парный тот же символ, но не ** (уже обработано выше).
                val end = findSingle(text, c, i + 1)
                if (end > i + 1) { flush(); withStyle(italic) { append(text, i + 1, end) }; i = end + 1; continue }
            }
        }
        buf.append(c); i++
    }
    flush()
}

/** Поиск парного маркера (двухсимвольного), пропуская вложенные однотипные. */
private fun findPair(text: String, marker: String, from: Int): Int {
    return text.indexOf(marker, from)
}

/** Поиск одинарного маркера, гарантирующий, что это не часть ** / __ (след. символ не равен c). */
private fun findSingle(text: String, c: Char, from: Int): Int {
    var j = from
    while (j < text.length) {
        if (text[j] == c) {
            // если за ним ещё один такой же — это ** или __, пропускаем оба
            if (j + 1 < text.length && text[j + 1] == c) { j += 2; continue }
            return j
        }
        j++
    }
    return -1
}
