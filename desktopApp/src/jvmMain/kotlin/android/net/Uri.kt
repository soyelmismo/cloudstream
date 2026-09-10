package android.net

import java.net.URI

class Uri private constructor(private val uri: URI) {
    override fun toString(): String = uri.toString()
    fun getScheme(): String? = uri.scheme
    fun getHost(): String? = uri.host
    fun getPath(): String? = uri.path
    fun getQuery(): String? = uri.query

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri {
            return try {
                Uri(URI.create(uriString))
            } catch (_: Throwable) {
                Uri(URI("http", "localhost", null, null))
            }
        }
    }
}
