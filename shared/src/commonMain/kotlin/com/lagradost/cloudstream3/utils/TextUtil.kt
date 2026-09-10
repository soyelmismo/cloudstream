package com.lagradost.cloudstream3.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.jvm.JvmName

sealed class UiText {
    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value
        override fun equals(other: Any?): Boolean = (other as? DynamicString)?.value == value
        override fun hashCode(): Int = value.hashCode()
    }

    class ComposeStringResource(
        val resource: StringResource,
        val args: List<Any>
    ) : UiText() {
        override fun toString(): String = "resource = $resource\nargs = $args"
        override fun equals(other: Any?): Boolean {
            if (other !is ComposeStringResource) return false
            return this.resource == other.resource && this.args == other.args
        }
        override fun hashCode(): Int = 31 * resource.hashCode() + args.hashCode()
    }
}

/**
 * Resolves this [UiText] to a localized [String] from within a coroutine using the suspend
 * [getString] loader. Use this outside of composition (e.g. in a ViewModel job) so resource
 * loading never blocks the calling thread.
 */
suspend fun UiText.asStringSuspend(): String {
    return when (this) {
        is UiText.DynamicString -> value
        is UiText.ComposeStringResource ->
            if (args.isEmpty()) getString(resource)
            else getString(resource, *args.toTypedArray())
    }
}

/**
 * Resolves this [UiText] to a localized [String] within a Composable context using
 * [stringResource]. Use this in the UI layer so strings are resolved at composition time
 * instead of via the (suspend) [getString].
 */
@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.DynamicString -> value
        is UiText.ComposeStringResource -> stringResource(resource, *args.toTypedArray())
    }
}

fun txt(value: String): UiText = UiText.DynamicString(value)

@JvmName("txtNull")
fun txt(value: String?): UiText? = value?.let { UiText.DynamicString(it) }

fun txt(resource: StringResource, vararg args: Any): UiText =
    UiText.ComposeStringResource(resource, args.toList())

@JvmName("txtNullResource")
fun txt(resource: StringResource?, vararg args: Any?): UiText? {
    if (resource == null || args.any { it == null }) return null
    return UiText.ComposeStringResource(resource, args.filterNotNull().toList())
}
