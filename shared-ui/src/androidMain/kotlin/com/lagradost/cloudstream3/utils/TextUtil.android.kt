package com.lagradost.cloudstream3.utils

import android.content.Context

private const val ANDROID_STRING_RES_TYPE = "string"
private const val RESOURCE_NOT_FOUND = 0

/**
 * Resolves this [UiText] synchronously through the Android resource table, mirroring the
 * original Android behaviour (`context.getString(resId)`).
 *
 * Compose Multiplatform string resources keep the same name as their `strings.xml` entry, so the
 * identifier can be looked up by name instead of blocking on the suspend resource loader. Use
 * [asStringSuspend] in coroutines and the `@Composable asString()` overload in the UI layer.
 */
fun UiText.asString(context: Context): String {
    return when (this) {
        is UiText.DynamicString -> value
        is UiText.ComposeStringResource -> {
            val name = resource.key.substringAfterLast(':')
            context.getStringByName(name, args) ?: name
        }
    }
}

private fun Context.getStringByName(name: String, args: List<Any>): String? {
    val resId = resources.getIdentifier(name, ANDROID_STRING_RES_TYPE, packageName)
    if (resId == RESOURCE_NOT_FOUND) return null
    return if (args.isEmpty()) getString(resId) else getString(resId, *args.toTypedArray())
}
