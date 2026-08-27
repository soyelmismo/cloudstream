package com.lagradost.cloudstream3.utils

actual object GitInfo {
    actual fun currentCommitHash(): String = try {
        GitInfo::class.java.classLoader?.getResourceAsStream("git-hash.txt")?.bufferedReader()?.readText()?.trim() ?: ""
    } catch (_: Throwable) {
        ""
    }
}
