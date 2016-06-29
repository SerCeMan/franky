package me.serce.franky.ui

import me.serce.franky.Protocol
import me.serce.franky.ui.flame.FlameComponent
import me.serce.franky.ui.flame.FlameTree
import me.serce.franky.ui.flame.FrameComponent
import javax.swing.JComponent

class ProfResultViewModel(result: Protocol.Response) : ViewModel {
    val profInfo = result.profInfo

    override fun createComponent(): JComponent {
        val tree = FlameTree(profInfo.samplesList)
        val methods: Map<Long, Protocol.MethodInfo> = profInfo.methodInfosList.associateBy({ it.jMethodId }, { it })
        return FlameComponent(tree, { methods[it] })
    }
}