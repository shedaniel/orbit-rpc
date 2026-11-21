package me.shedaniel.discordrpc.core.rules

/**
 * Minimal template language:
 *  - `{name}` is replaced by the value of `name` (empty when missing).
 *  - `[ ... ]` is an optional section; it is dropped when any placeholder inside it is empty.
 *  - `\[`, `\]`, `\{` escape literal brackets.
 * Whitespace is collapsed and trimmed afterwards.
 */
object Template {
    val placeholders = listOf("title", "artist", "album", "app", "device", "kind")

    fun render(template: String, values: Map<String, String?>): String {
        if (template.isEmpty()) return ""
        val out = StringBuilder()
        renderInto(template, 0, template.length, values, out)
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** Returns false if a placeholder was empty. */
    private fun renderInto(t: String, from: Int, to: Int, values: Map<String, String?>, out: StringBuilder): Boolean {
        var ok = true
        var i = from
        while (i < to) {
            val c = t[i]
            when {
                c == '\\' && i + 1 < to -> { out.append(t[i + 1]); i += 2 }
                c == '{' -> {
                    val end = t.indexOf('}', i + 1).takeIf { it in 0 until to }
                    if (end == null) { out.append(c); i++ } else {
                        val key = t.substring(i + 1, end).trim()
                        val v = values[key]
                        if (v.isNullOrBlank()) ok = false else out.append(v)
                        i = end + 1
                    }
                }
                c == '[' -> {
                    val end = matchingBracket(t, i, to)
                    if (end == null) { out.append(c); i++ } else {
                        val section = StringBuilder()
                        if (renderInto(t, i + 1, end, values, section)) out.append(section)
                        i = end + 1
                    }
                }
                else -> { out.append(c); i++ }
            }
        }
        return ok
    }

    private fun matchingBracket(t: String, open: Int, to: Int): Int? {
        var depth = 0
        var i = open
        while (i < to) {
            when (t[i]) {
                '\\' -> i++
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }
}
