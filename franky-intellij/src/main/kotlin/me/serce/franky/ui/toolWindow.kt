package me.serce.franky.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import me.serce.franky.jvm.AttachableJVM
import me.serce.franky.util.Lifetime
import me.serce.franky.util.create
import rx.Observable
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTabbedPane

class FrankyToolWindowFactory() : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val frankyPanelController = FrankyPanelController()
        val frankyPanel = frankyPanelController.createComponent()
        toolWindow.component.add(frankyPanel)
    }
}

class FrankyPanelController() : ViewModel {
    val lifetime: Lifetime = Lifetime()

    override fun createComponent(): JComponent {
        val jvmsListController: JvmsListViewModel = JvmsListViewModelImpl(lifetime.create())
        val profilingTabsController = JvmTabsViewModel(lifetime.create(), jvmsListController.connectJvmPublisher)

        return BorderLayoutPanel().apply {
            addToLeft(jvmsListController.createComponent()).apply {
                preferredSize = Dimension(150, 0)
            }
            addToCenter(profilingTabsController.createComponent()).apply {
            }
        }
    }
}

class JvmTabsViewModel(val lifetime: Lifetime, jvmPublisher: Observable<AttachableJVM>) {
    val view = JvmTabsView()

    init {
        jvmPublisher.subscribe { vm ->
            val tabController = JvmTabViewModel(lifetime.create(), vm)
            view.addTab(vm.id, tabController.createComponent())
        }
    }

    fun createComponent(): Component = view.createComponent()

    class JvmTabsView() : View {
        val tabs = JTabbedPane()

        fun addTab(name: String, comp: JComponent) {
            tabs.addTab(name, comp)
        }

        override fun createComponent() = tabs
    }
}
