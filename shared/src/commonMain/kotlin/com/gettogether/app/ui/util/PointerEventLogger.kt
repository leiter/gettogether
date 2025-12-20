package com.gettogether.app.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Logs pointer events (touch coordinates) without consuming them.
 * Useful for recording UI coordinates during testing.
 *
 * @param tag Tag for log messages
 * @param enabled Whether logging is enabled (default true)
 */
fun Modifier.logPointerEvents(tag: String = "PointerEvent", enabled: Boolean = true): Modifier {
    if (!enabled) return this

    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { change ->
                    if (change.pressed && change.previousPressed != change.pressed) {
                        val x = change.position.x
                        val y = change.position.y
                        println("[$tag] Touch DOWN at (${x.toInt()}, ${y.toInt()})")
                    }
                }
                // Do NOT consume the event - let it pass through to underlying UI
            }
        }
    }
}
