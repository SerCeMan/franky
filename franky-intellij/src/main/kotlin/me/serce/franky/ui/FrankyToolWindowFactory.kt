package me.serce.franky.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import me.serce.franky.AttachableJVM
import me.serce.franky.JVMAttachService
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*

class FrankyToolWindowFactory(val jvmAttachService: JVMAttachService) : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val jmvsPanel = createFrankyPanel()
        toolWindow.component.add(jmvsPanel)
    }

    private fun createFrankyPanel(): JPanel {
        val jvmsList = JComboBox<AttachableJVM>().apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component? {
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                        value as AttachableJVM
                        text = "${value.id} ${value.name.substring(0, Math.min(value.name.length, 20))}"
                    }
                }
            }

            addActionListener {
                println("Hello! ${selectedItem}")
            }
        }

        val refreshButton = JButton().apply {
            text = "Refresh"
            addActionListener {
                refreshJvmsList(jvmsList)
            }
        }

        val connectButton = JButton().apply {
            text = "Connect"
            addActionListener {
                val session = jvmAttachService.connect(jvmsList.selectedItem as AttachableJVM)
            }
        }

        val jmvsPanel = JPanel().apply {
            layout = FlowLayout()

            add(refreshButton)
            add(jvmsList)
        }
        refreshJvmsList(jvmsList)
        return jmvsPanel
    }

    private fun refreshJvmsList(jvmsList: JComboBox<AttachableJVM>) {
        jvmsList.removeAllItems();
        jvmAttachService.attachableJVMs().forEach { jvmsList.addItem(it) }
    }
}
