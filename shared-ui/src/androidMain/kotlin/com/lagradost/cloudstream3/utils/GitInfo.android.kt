package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.api.getContext

actual object GitInfo {
    actual fun currentCommitHash(): String = try {
        val context = getContext() as? Context
        val fromAssets = context?.assets?.open("git-hash.txt")?.bufferedReader()?.readText()?.trim()
        if (!fromAssets.isNullOrBlank()) {
            fromAssets
        } else {
            GitInfo::class.java.classLoader?.getResourceAsStream("git-hash.txt")?.bufferedReader()?.readText()?.trim() ?: ""
        }
    } catch (_: Throwable) {
        ""
    }
}
