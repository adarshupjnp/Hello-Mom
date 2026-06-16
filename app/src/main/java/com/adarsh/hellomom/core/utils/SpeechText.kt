package com.adarsh.hellomom.core.utils

/**
 * Matches emoji and pictographic symbols so they can be removed before text is spoken aloud.
 *
 * Without this, TTS narrates symbols literally — e.g. the heart "❤️" is read as the word
 * "dil" in Hindi. We only strip symbol/emoji ranges; Latin, digits, punctuation and the
 * Indic scripts used in the app (Devanagari/Gujarati, all in the BMP below U+2300) are left
 * untouched, so the spoken sentence is unchanged apart from the dropped symbols.
 */
private val SPEECH_EMOJI_REGEX = Regex(
    "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]" + // emoji in supplementary planes (surrogate code units)
        "|[\\u2600-\\u26FF]" +           // Miscellaneous Symbols (☀ ☂ ☎ …)
        "|[\\u2700-\\u27BF]" +           // Dingbats (❤ ✨ ✔ …)
        "|[\\u2B00-\\u2BFF]" +           // Miscellaneous Symbols and Arrows (⭐ …)
        "|[\\u2300-\\u23FF]" +           // Miscellaneous Technical (⏰ ⌛ …)
        "|[\\u2190-\\u21FF]" +           // Arrows (← → …)
        "|[\\u24C2\\u3030\\u303D\\u3297\\u3299]" + // assorted symbols used as emoji
        "|[\\uFE00-\\uFE0F]" +           // Variation Selectors (e.g. the ️ after ❤)
        "|\\u200D"                       // Zero Width Joiner (combines emoji sequences)
)

/**
 * Returns [text] with emojis/icons removed, ready to be passed to text-to-speech.
 * Removed symbols become a space and runs of whitespace are collapsed so words never merge.
 * Display/notification text should keep using the original string — only spoken output is sanitized.
 */
fun sanitizeForSpeech(text: String): String =
    text.replace(SPEECH_EMOJI_REGEX, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
