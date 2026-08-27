package android.util

object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        return if ((flags and URL_SAFE) != 0) {
            java.util.Base64.getUrlEncoder().encodeToString(input)
        } else {
            java.util.Base64.getEncoder().encodeToString(input)
        }
    }

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray {
        return if ((flags and URL_SAFE) != 0) {
            java.util.Base64.getUrlEncoder().encode(input)
        } else {
            java.util.Base64.getEncoder().encode(input)
        }
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val clean = str.replace("\n", "").replace("\r", "").trim()
        return try {
            if ((flags and URL_SAFE) != 0) {
                java.util.Base64.getUrlDecoder().decode(clean)
            } else {
                java.util.Base64.getDecoder().decode(clean)
            }
        } catch (_: Throwable) {
            try {
                java.util.Base64.getDecoder().decode(clean)
            } catch (_: Throwable) {
                java.util.Base64.getUrlDecoder().decode(clean)
            }
        }
    }

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray {
        return decode(String(input), flags)
    }
}
