package me.serce.franky.ui.flame

import com.google.protobuf.CodedInputStream
import com.intellij.util.ui.components.BorderLayoutPanel
import gnu.trove.TIntObjectHashMap
import me.serce.franky.Protocol
import me.serce.franky.Protocol.*
import rx.lang.kotlin.PublishSubject
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.FileInputStream
import java.util.*
import javax.swing.*

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
    }

    val methodInfoSubject = PublishSubject<MethodInfo>()
    private val nodeToComp = hashMapOf<FlameNode, JComponent>()
    var currentRoot = tree.root

    init {
        layout = null
        recalcPositions()
    }

    private fun build(node: FlameNode, begin: Double, end: Double, level: Int) {
        val width = end - begin
        if (width <= 0) {
            return
        }
        val comp = nodeToComp.getOrPut(node, {
            val methodInfo = frameFactory(node.methodId) ?: rootMethodInfo()
            FrameComponent(methodInfo).apply {
                this@FlameComponent.add(this)
                expandPublisher.subscribe {
                    resetNode(node)
                }
            }
        })

        val coord = ComponentCoord(begin, width, level)
        val dim = Dimension(coord.getWidth(getWidth()), coord.getHeight())
        val p = Point(coord.getX(getWidth()), coord.getY())
        comp.apply {
            size = dim
            location = p
        }

        val totalCost = node.selfCost + node.children.map { it.value.cost }.sum()
        var nodeBegin = begin
        for ((id, vertex) in node.children) {
            val nodeWidth = width * (vertex.cost / totalCost.toDouble())
            build(vertex.node, nodeBegin, nodeBegin + nodeWidth, level + 1)
            nodeBegin += nodeWidth
        }
    }

    private fun resetToRoot() {
        currentRoot = tree.root
    }

    private fun resetNode(node: FlameNode) {
        currentRoot = node
        for (c in components) {
            c.size = Dimension(0, 0)
            c.location = Point(0, 0)
        }
        recalcPositions()
        validate()
        repaint()
    }


    override fun paintComponent(g: Graphics) {
        recalcPositions()
        super.paintComponent(g)
    }

    private fun recalcPositions() = build(currentRoot, 0.0, 1.0, 0)
    private fun rootMethodInfo() = MethodInfo.newBuilder().setJMethodId(0).setHolder("").setName("").setSig("").build()
}


class FrameComponent(methodInfo: MethodInfo) : BorderLayoutPanel() {
    val expandPublisher = PublishSubject<ActionEvent>()

    private val expandBtn = JButton("expand").apply {
        addActionListener { expandPublisher.onNext(it) }
    }

    init {
        addToCenter(JButton(methodInfo.name.toString()))
        addToRight(expandBtn)
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
            repaint()
        }
    }
}