package com.lagradost.cloudstream3.shared.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.jvm.JvmInline

@Immutable
@JvmInline
value class Layout(val flags: Int) {
    fun has(other: Layout): Boolean = (flags and other.flags) != 0
    fun has(otherFlags: Int): Boolean = (flags and otherFlags) != 0

    infix fun or(other: Layout): Layout = Layout(this.flags or other.flags)
    infix fun and(other: Layout): Layout = Layout(this.flags and other.flags)

    companion object {
        val NONE = Layout(0)
        val PHONE = Layout(0b0001)
        val TV = Layout(0b0010)
        val EMULATOR = Layout(0b0100)
        val COMPUTER = Layout(0b1000)
        val DESKTOP = COMPUTER
        val ALL = Layout(0b1111)
    }
}

val LocalLayout: ProvidableCompositionLocal<Layout> = staticCompositionLocalOf { Layout.PHONE }

@Composable
@ReadOnlyComposable
fun isLayout(flags: Layout): Boolean = LocalLayout.current.has(flags)

@Composable
fun isLayoutState(flags: Layout): State<Boolean> {
    val currentLayout = LocalLayout.current
    return remember(currentLayout, flags) {
        derivedStateOf { currentLayout.has(flags) }
    }
}
