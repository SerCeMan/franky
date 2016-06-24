package me.serce.franky.ui.flame

import me.serce.franky.Protocol.CallTraceSampleInfo
import java.awt.Graphics
import java.util.*
import javax.swing.JPanel

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

class FlameNode(val methodId: Int) {
    var selfCost: Int = 0;
    val children: HashMap<Int, FlameVertex> = hashMapOf()
}

class FlameComponent(val tree: FlameTree) : JPanel() {
    val cellHeigh = 10

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        drawLevel(tree.root, g, 0, width, 0)
    }

    private fun drawLevel(node: FlameNode, g: Graphics, begin: Int, end: Int, height: Int) {
        val width = end - begin
        if (width <= 0) {
            return
        }
        g.drawRect(begin, height, end, height + cellHeigh)
        val totalCost = node.selfCost + node.children.map { it.value.cost }.sum()
        var nodeBegin = 0
        for ((id, vertex) in node.children) {
            val nodeWidth = (width * (vertex.cost / totalCost.toDouble())).toInt()
            nodeBegin += nodeWidth
            drawLevel(vertex.node, g, nodeBegin, nodeBegin + nodeWidth, height + cellHeigh)
        }
    }
}

fun main(args: Array<String>) {

}