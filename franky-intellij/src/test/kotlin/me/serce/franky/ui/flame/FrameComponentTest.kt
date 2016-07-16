package me.serce.franky.ui.flame

import com.google.protobuf.CodedInputStream
import com.intellij.testFramework.IdeaTestCase
import me.serce.franky.Protocol
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.Toolkit
import java.io.FileInputStream
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * Created by Sergey.Tselovalnikov on 7/15/16.
 */
class FrameComponentTest : IdeaTestCase() {
    fun testEmpty() {
    }

    fun itestComponent() {
        SwingUtilities.invokeLater {
            val result = Protocol.Response.parseFrom(CodedInputStream.newInstance(FileInputStream("/home/serce/tmp/ResultData")))
            val profInfo = result.profInfo
            val samples = profInfo.samplesList
            val methods: Map<Long, Protocol.MethodInfo> = profInfo.methodInfosList.associateBy({ it.jMethodId }, { it })


            val tree = FlameTree(samples)
            val panel = JFrame().apply {
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                pack()
                size = Dimension(800, 600)
                contentPane.apply {
                    add(JScrollPane(FlameComponent(tree, { methods[it] }).apply {
                        size = Dimension(800, 600)
                        methodInfoSubject.subscribe {
                            println("CLICK $it")
                        }
                    }).apply {
                        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
                        verticalScrollBar.unitIncrement = 16
                    })
                }
                isVisible = true
                repaint()
            }
        }
        val eventQueue = Toolkit.getDefaultToolkit().systemEventQueue
        while (true) {
            val event = eventQueue.nextEvent
            eventQueue.javaClass.getDeclaredMethod("dispatchEvent", AWTEvent::class.java).invoke(eventQueue, event)
        }
    }
}