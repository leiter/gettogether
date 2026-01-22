package com.gettogether.app.data.util

/**
 * Utility for parsing vCard data.
 * vCard is used in Jami for exchanging profile information during trust requests.
 *
 * Typical vCard format:
 * ```
 * BEGIN:VCARD
 * VERSION:2.1
 * FN:Display Name
 * PHOTO;ENCODING=BASE64;TYPE=PNG:...
 * END:VCARD
 * ```
 */
object VCardParser {

    /**
     * Parse a vCard byte array and extract profile information.
     * @param payload The vCard data as bytes
     * @return Parsed VCardProfile or null if parsing fails
     */
    fun parse(payload: ByteArray): VCardProfile? {
        if (payload.isEmpty()) {
            println("VCardParser: Payload is empty")
            return null
        }

        return try {
            val vcard = payload.decodeToString()
            println("VCardParser: Raw payload (${payload.size} bytes):")
            println("VCardParser: ---START---")
            println(vcard)
            println("VCardParser: ---END---")
            parseString(vcard)
        } catch (e: Exception) {
            println("VCardParser: Failed to parse vCard: ${e.message}")
            println("VCardParser: Payload bytes: ${payload.take(100).joinToString(",") { it.toString() }}")
            null
        }
    }

    /**
     * Parse a vCard string and extract profile information.
     * @param vcard The vCard data as string
     * @return Parsed VCardProfile or null if parsing fails
     */
    fun parseString(vcard: String): VCardProfile? {
        println("VCardParser.parseString: Input length=${vcard.length}")

        if (!vcard.contains("BEGIN:VCARD", ignoreCase = true)) {
            println("VCardParser.parseString: No BEGIN:VCARD found")
            return null
        }
        println("VCardParser.parseString: Found BEGIN:VCARD")

        val displayName = extractField(vcard, "FN")
        println("VCardParser.parseString: FN field = '$displayName'")

        val photoData = extractPhoto(vcard)
        println("VCardParser.parseString: Has photo = ${photoData != null}")

        // If we couldn't extract any useful data, return null
        if (displayName == null && photoData == null) {
            println("VCardParser.parseString: No useful data found, returning null")
            return null
        }

        println("VCardParser.parseString: Success! displayName='$displayName'")
        return VCardProfile(
            displayName = displayName,
            photoBase64 = photoData
        )
    }

    /**
     * Extract a simple field value from vCard.
     * Handles both "FIELD:value" and "FIELD;params:value" formats.
     */
    private fun extractField(vcard: String, fieldName: String): String? {
        println("VCardParser.extractField: Looking for field '$fieldName'")

        // Match pattern: FIELDNAME:value or FIELDNAME;params:value
        // The value continues until newline
        val patterns = listOf(
            Regex("(?i)^$fieldName:(.+?)(?:\r?\n|\$)", RegexOption.MULTILINE),
            Regex("(?i)^$fieldName;[^:]*:(.+?)(?:\r?\n|\$)", RegexOption.MULTILINE)
        )

        for ((index, pattern) in patterns.withIndex()) {
            println("VCardParser.extractField: Trying pattern $index: ${pattern.pattern}")
            val match = pattern.find(vcard)
            if (match != null) {
                val value = match.groupValues[1].trim()
                println("VCardParser.extractField: Pattern $index matched! Raw value='${match.groupValues[1]}', trimmed='$value'")
                if (value.isNotEmpty()) {
                    return value
                }
            } else {
                println("VCardParser.extractField: Pattern $index did not match")
            }
        }
        println("VCardParser.extractField: No pattern matched for '$fieldName'")
        return null
    }

    /**
     * Extract photo data from vCard.
     * Returns base64-encoded photo data if present.
     */
    private fun extractPhoto(vcard: String): String? {
        // PHOTO field can span multiple lines with base64 data
        // Format: PHOTO;ENCODING=BASE64;TYPE=PNG:base64data...
        // Using (?is) inline flags: i=ignore case, s=dot matches all (including newlines)
        val photoPattern = Regex(
            "(?is)PHOTO;[^:]*:([A-Za-z0-9+/=\\s]+?)(?=\r?\n[A-Z]|\r?\nEND:)"
        )

        val match = photoPattern.find(vcard)
        if (match != null) {
            // Remove whitespace from base64 data
            return match.groupValues[1].replace("\\s".toRegex(), "")
        }
        return null
    }

    /**
     * Create a simple vCard string with display name.
     * @param displayName The display name to include
     * @param photoBase64 Optional base64-encoded photo data
     * @return vCard string
     */
    fun create(displayName: String, photoBase64: String? = null): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCARD")
        sb.appendLine("VERSION:2.1")
        sb.appendLine("FN:$displayName")

        if (!photoBase64.isNullOrEmpty()) {
            sb.appendLine("PHOTO;ENCODING=BASE64;TYPE=PNG:$photoBase64")
        }

        sb.appendLine("END:VCARD")
        return sb.toString()
    }
}

/**
 * Represents parsed profile data from a vCard.
 */
data class VCardProfile(
    val displayName: String?,
    val photoBase64: String? = null
)
