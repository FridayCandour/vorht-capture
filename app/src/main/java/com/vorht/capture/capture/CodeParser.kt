package com.vorht.capture.capture

/**
 * Extracts a verification code from a WhatsApp message body.
 *
 * Strategy (plain text from the notification — no OCR):
 *  1. Token shortly after a keyword like "code", "OTP", "pin", "BIRTH", "verification"
 *     — must be a single 4-8 char token containing at least one digit.
 *  2. Dashed code like 482-913.
 *  3. Standalone alphanumeric token containing at least one digit (4-8 chars), e.g. "BIRTH123".
 *  4. Plain digits.
 * Returns null when nothing plausible is found -> event goes to REVIEW.
 *
 * History note: an earlier raw-string + .replace() placeholder trick produced
 * "{{4,8}}" and threw PatternSyntaxException at class-init (crash on HONOR
 * LNA-NX1). All patterns are verified against a JVM test harness.
 */
object CodeParser {

    private const val MAX_LEN = 8
    private const val MIN_LEN = 4

    /** Keyword then up to 10 non-word chars, then a single 4-8 char token. */
    private val keywordHint = Regex(
        "(?i)(?:code|otp|pin|password|verification|verify|birth)\\W{0,10}([A-Za-z0-9-]{4,8})"
    )
    private val dashedCode = Regex("\\b\\d{3}-\\d{3}\\b")
    private val alnumWithDigit = Regex(
        "\\b(?=[A-Za-z0-9-]*\\d)[A-Za-z0-9-]{$MIN_LEN,$MAX_LEN}\\b"
    )
    private val digitToken = Regex("\\b\\d{$MIN_LEN,$MAX_LEN}\\b")

    /** Clean a candidate token: strip dashes, require 4-8 chars incl. a digit. */
    private fun cleaned(token: String): String? {
        val c = token.replace("-", "").uppercase()
        return if (c.length in MIN_LEN..MAX_LEN && c.any { it.isDigit() }) c else null
    }

    fun parse(message: String): String? {
        keywordHint.find(message)?.groupValues?.get(1)?.let { token ->
            cleaned(token)?.let { return it }
        }

        dashedCode.find(message)?.let { return it.value.replace("-", "") }

        alnumWithDigit.find(message)?.let { return cleaned(it.value) ?: it.value.uppercase() }

        digitToken.find(message)?.let { return it.value }

        return null
    }
}
