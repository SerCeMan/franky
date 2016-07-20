package me.serce.franky.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.JBTabsPaneImpl
import com.intellij.ui.components.JBLabel
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class MouseClickListener(val handle: (MouseEvent?) -> Unit) : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) = handle(e)
}

inline fun jPanel(block: JPanel.() -> Unit): JPanel = JPanel().apply(block)
inline fun jButton(block: JButton.() -> Unit): JButton = JButton().apply(block)
inline fun borderLayoutPanel(block: BorderLayoutPanel.() -> Unit): BorderLayoutPanel = BorderLayoutPanel().apply(block)
inline fun jbLabel(block: JBLabel.() -> Unit): JBLabel = JBLabel().apply(block)
inline fun jbLabel(title: String, block: JBLabel.() -> Unit): JBLabel = JBLabel(title).apply(block)
inline fun tabInfo(comp: JComponent, block: TabInfo.() -> Unit): TabInfo = TabInfo(comp).apply(block)


class CloseAction(val onClose: (AnActionEvent) -> Unit) : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.apply {
            icon = AllIcons.Actions.Close
            hoveredIcon = AllIcons.Actions.CloseHovered
            isVisible = UISettings.getInstance().SHOW_CLOSE_BUTTON
            text = "Finish profiling"
        }
    }

    override fun actionPerformed(e: AnActionEvent) = onClose(e)
}

