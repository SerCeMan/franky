package me.serce.franky.ui

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class MouseClickListener(val handle: (MouseEvent?) -> Unit) : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) = handle(e)
}
