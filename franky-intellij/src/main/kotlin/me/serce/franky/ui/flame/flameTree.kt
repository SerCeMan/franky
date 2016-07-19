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
class FlameTree(sampleInfo: List<CallTraceSampleInfo>) {
    val root: FlameNode = FlameNode(0, null)

    init {
        for (sample in sampleInfo) {
            sample.validate()
            addSampleToTree(sample)
        }
    }

    private fun addSampleToTree(sample: CallTraceSampleInfo) {
        val coef = sample.callCount
        root.cost += coef

        var node = root
        for (frame in sample.frameList.reversed()) {
            val methodId = frame.jMethodId
            node = node.children.computeIfAbsent(methodId, {
                FlameNode(frame.jMethodId, node)
            })
            node.cost += coef
        }
    }
}

class FlameNode(val methodId: Long, val parent: FlameNode?) {
    var cost: Int = 0
    val children: HashMap<Long, FlameNode> = hashMapOf()
}
