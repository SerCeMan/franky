package me.serce.franky.ui.flame

import com.google.protobuf.CodedInputStream
import com.intellij.util.ui.components.BorderLayoutPanel
import gnu.trove.TIntObjectHashMap
import me.serce.franky.Protocol
import me.serce.franky.Protocol.*
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.FileInputStream
import java.util.*
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun CallTraceSampleInfo.validate() {
    if (frameList.isEmpty()) {
        throw IllegalArgumentException("Empty trace sample $this")
    }
}

class FlameTree(val sampleInfo: List<CallTraceSampleInfo>) {
    val root: FlameNode = FlameNode(0)

    init {
        for (sample in sampleInfo) {
            sample.validate()
            addSampleToTree(sample)
        }
    }

    private fun addSampleToTree(sample: CallTraceSampleInfo) {
        val coef = sample.callCount

        var node = root
        for (frame in sample.frameList) {
            val methodId = frame.jMethodId
            node = node.children.computeIfAbsent(methodId, {
                FlameVertex(coef, FlameNode(frame.jMethodId))
            }).node
        }
        node.selfCost += coef
    }
}

data class FlameVertex(var cost: Int, val node: FlameNode)

class FlameNode(val methodId: Long) {
    var selfCost: Int = 0;
    val children: HashMap<Long, FlameVertex> = hashMapOf()
}

class FlameComponent(val tree: FlameTree, val frameFactory: (Long) -> FrameComponent) : JComponent() {
    data class RectObject(val x: Int, val y: Int, val width: Int, val heigh: Int, val node: FlameNode, val comp: FrameComponent)

    companion object {
        const val cellHeigh = 20
    }

    var root = tree.root
    val xLine: ArrayList<RectObject> = arrayListOf()

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        xLine.clear()
        g.font = Font("Default", Font.PLAIN, 10)
        drawLevel(root, g, 0, width, 0)
    }

    private fun drawLevel(node: FlameNode, g: Graphics, begin: Int, end: Int, height: Int) {
        val width = end - begin
        if (width <= 0) {
            return
        }
        val frameComponent = frameFactory(node.methodId)
        frameComponent.paintComponent(g, begin, height, width, cellHeigh)

        val record = RectObject(begin, height, width, cellHeigh, node, frameComponent)
        xLine.add(record)

        val totalCost = node.selfCost + node.children.map { it.value.cost }.sum()
        var nodeBegin = begin
        for ((id, vertex) in node.children) {
            val nodeWidth = (width * (vertex.cost / totalCost.toDouble())).toInt()
            drawLevel(vertex.node, g, nodeBegin, nodeBegin + nodeWidth, height + cellHeigh)
            nodeBegin += nodeWidth
        }
    }
}

class FrameComponent(mInfo: MethodInfo?) {
    val methodInfo = mInfo ?: rootMethodInfo()

    fun paintComponent(g: Graphics, x: Int, y: Int, width: Int, heigh: Int) {
        g.drawRect(x, y, width, heigh)
        if (width > 50 && methodInfo.jMethodId != 0L) {
            drawBody(g, heigh, x, y)
        }
    }

    private fun drawBody(g: Graphics, heigh: Int, x: Int, y: Int) {
        g.drawString("${methodInfo.sig} ${methodInfo.name}", x + 2, y + heigh - 5)
    }

    private fun rootMethodInfo() = MethodInfo.newBuilder().setJMethodId(0).setHolder("").setName("").setSig("").build()
}

fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
        val result = Protocol.Response.parseFrom(CodedInputStream.newInstance(FileInputStream("/home/serce/tmp/ResultData")))
        val profInfo = result.profInfo
        val samples = profInfo.samplesList
        val methods: Map<Long, MethodInfo> = profInfo.methodInfosList.associateBy({ it.jMethodId }, { it })


        val tree = FlameTree(samples)
        val panel = JFrame().apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            pack()
            size = Dimension(800, 600)
            contentPane.apply {
                add(FlameComponent(tree, { FrameComponent(methods[it]) }).apply {
                    size = Dimension(800, 600)
                })
            }
            isVisible = true
        }
    }
}