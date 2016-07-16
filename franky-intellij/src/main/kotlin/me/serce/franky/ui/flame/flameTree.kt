package me.serce.franky.ui.flame

import me.serce.franky.Protocol.CallTraceSampleInfo
import java.util.*


fun CallTraceSampleInfo.validate() {
    if (frameList.isEmpty()) {
        throw IllegalArgumentException("Empty trace sample $this")
    }
}

/**
 * Represents tree of stack traces
 */
class FlameTree(val sampleInfo: List<CallTraceSampleInfo>) {
    val root: FlameNode = FlameNode(0, null)

    init {
        for (sample in sampleInfo) {
            sample.validate()
            addSampleToTree(sample)
        }
    }

    private fun addSampleToTree(sample: CallTraceSampleInfo) {
        val coef = sample.callCount

        var node = root
        for (frame in sample.frameList.reversed()) {
            val methodId = frame.jMethodId
            node = node.children.computeIfAbsent(methodId, {
                FlameVertex(coef, FlameNode(frame.jMethodId, node))
            }).node
        }
        node.selfCost += coef
    }
}

data class FlameVertex(var cost: Int, val node: FlameNode)

class FlameNode(val methodId: Long, val parent: FlameNode?) {
    var selfCost: Int = 0;
    val children: HashMap<Long, FlameVertex> = hashMapOf()
}
