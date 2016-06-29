package me.serce.franky.ui.flame

import com.google.protobuf.CodedInputStream
import com.intellij.util.ui.components.BorderLayoutPanel
import gnu.trove.TIntObjectHashMap
import me.serce.franky.Protocol
import me.serce.franky.Protocol.*
import rx.lang.kotlin.PublishSubject
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


//////////////// components

class FlameComponent(private val tree: FlameTree, val frameFactory: (Long) -> MethodInfo?) : JComponent() {
    data class ComponentCoord(val x: Double, val width: Double, val level: Int) {
        companion object {
            const val frameHeight = 20
        }

        fun getX(parentWidth: Int) = (x * parentWidth).toInt()
        fun getWidth(parentWidth: Int) = (width * parentWidth).toInt()
        fun getY() = level * frameHeight
        fun getHeight() = frameHeight

        fun isIn(point: Double) = x < point && point < x + width

        fun isIn(rootCoords: ComponentCoord) = x <= rootCoords.x + rootCoords.width || x + width >= rootCoords.x
    }

    val methodInfoSubject = PublishSubject<MethodInfo>()

    private val components = hashMapOf<ComponentCoord, FrameComponent>()
    private val nodeToCoord = hashMapOf<FlameNode, ComponentCoord>()
    private var currentRoot = tree.root

    init {
        build(tree.root, 0.0, 1.0, 0)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val x = e.x / width.toDouble()
                val level = e.y / ComponentCoord.frameHeight
                // todo sloooow
                for ((coord, comp) in components) {
                    if (coord.level == level && coord.isIn(x)) {
                        methodInfoSubject.onNext(comp.methodInfo)
                    }
                }
            }
        })
    }

    fun resetToRoot() {
        currentRoot = tree.root
    }

    private fun build(node: FlameNode, begin: Double, end: Double, level: Int) {
        val width = end - begin
        if (width <= 0) {
            return
        }
        val methodInfo = frameFactory(node.methodId) ?: rootMethodInfo()
        val coord = ComponentCoord(begin, width, level)
        nodeToCoord[node] = coord
        components[coord] = FrameComponent(methodInfo)

        val totalCost = node.selfCost + node.children.map { it.value.cost }.sum()
        var nodeBegin = begin
        for ((id, vertex) in node.children) {
            val nodeWidth = width * (vertex.cost / totalCost.toDouble())
            build(vertex.node, nodeBegin, nodeBegin + nodeWidth, level + 1)
            nodeBegin += nodeWidth
        }
    }


    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.font = Font("Default", Font.PLAIN, 10)
        val rootCoords = nodeToCoord[currentRoot]!!
        for ((coord, component) in components) {
            if (coord.isIn(rootCoords)) {
                component.paintComponent(g, coord.getX(width), coord.getY(), coord.getWidth(width), coord.getHeight())
            }
        }
    }

    private fun rootMethodInfo() = MethodInfo.newBuilder().setJMethodId(0).setHolder("").setName("").setSig("").build()
}


class FrameComponent(val methodInfo: MethodInfo) {
    fun paintComponent(g: Graphics, x: Int, y: Int, width: Int, heigh: Int) {
        g.drawRect(x, y, width, heigh)
        if (width > 50 && methodInfo.jMethodId != 0L) {
            drawBody(g, heigh, x, y)
        }
    }

    private fun drawBody(g: Graphics, heigh: Int, x: Int, y: Int) {
        g.drawString("${methodInfo.sig} ${methodInfo.name}", x + 2, y + heigh - 5)
    }

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
                add(FlameComponent(tree, { methods[it] }).apply {
                    size = Dimension(800, 600)
                    methodInfoSubject.subscribe {
                        println("CLICK $it")
                    }
                })
            }
            isVisible = true
        }
    }
}