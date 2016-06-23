package me.serce.franky.ui.flame

import me.serce.franky.Protocol.CallTraceSampleInfo
import java.util.*

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

fun main(args: Array<String>) {

}