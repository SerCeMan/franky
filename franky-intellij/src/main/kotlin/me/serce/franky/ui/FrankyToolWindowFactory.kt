package me.serce.franky.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import me.serce.franky.AttachableJVM
import me.serce.franky.JVMAttachService
import me.serce.franky.Protocol
import me.serce.franky.Protocol.Response
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
        }

        val jmvsPanel = JPanel().apply {
            layout = FlowLayout()

            add(refreshButton)
            add(jvmsList)
            add(connectButton)
        }

        val frankyPanel = JPanel()

        frankyPanel.add(jmvsPanel)


        connectButton.addActionListener {
            val session = jvmAttachService.connect(jvmsList.selectedItem as AttachableJVM)

            val resultArea = JTextArea()
            session.addResultListener { res: Response ->
                println("RESULT $res")
                resultArea.text = res.toString()
            }


            val startProfilingButton = JButton("Start profiling")
            startProfilingButton.addActionListener {
                session.startProfiling()
            }

            val stopProfilingButton = JButton("Stop profiling")
            stopProfilingButton.addActionListener {
                session.stopProfiling()
            }

            frankyPanel.add(JPanel().apply {
                layout = FlowLayout()

                add(startProfilingButton)
                add(stopProfilingButton)
                add(resultArea)
            })
        }

        refreshJvmsList(jvmsList)
        return frankyPanel
    }

    private fun refreshJvmsList(jvmsList: JComboBox<AttachableJVM>) {
        jvmsList.removeAllItems();
        jvmAttachService.attachableJVMs().forEach { jvmsList.addItem(it) }
    }
}
