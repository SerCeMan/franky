package me.serce.franky.ui

import com.intellij.ui.components.JBLabel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel

class MouseClickListener(val handle: (MouseEvent?) -> Unit) : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) = handle(e)
}

fun JPanel(block: JPanel.() -> Unit): JPanel = JPanel().apply(block)
fun JButton(block: JButton.() -> Unit): JButton = JButton().apply(block)
fun JBLabel(block: JBLabel.() -> Unit): JBLabel = JBLabel().apply(block)

