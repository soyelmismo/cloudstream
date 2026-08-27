package android.content

import java.io.File

open class Context {
    open fun getPackageName(): String = "com.lagradost.cloudstream3"
    open fun getFilesDir(): File = File(System.getProperty("user.home") ?: ".", ".cloudstream")
    open fun getCacheDir(): File = File(getFilesDir(), "cache")
}
