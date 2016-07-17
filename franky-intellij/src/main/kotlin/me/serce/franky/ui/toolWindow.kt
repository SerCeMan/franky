package me.serce.franky.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import me.serce.franky.FrankyComponent
import me.serce.franky.jvm.AttachableJVM
import me.serce.franky.util.Lifetime
import me.serce.franky.util.create
import me.serce.franky.util.toDisposable
import rx.Observable
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTabbedPane

class FrankyToolWindowFactory(val frankyComponent: FrankyComponent) : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val lifetime = frankyComponent.rootLifetime.create()
        val frankyPanelController = FrankyPanelController(lifetime)
        val frankyPanel = frankyPanelController.createComponent()

        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(frankyPanel, null, true)
        contentManager.addContent(content)
        Disposer.register(project, lifetime.toDisposable())
    }
}

class FrankyPanelController(val lifetime: Lifetime) : ViewModel {

    override fun createComponent(): JComponent {
        val jvmsListController: JvmsListViewModel = JvmsListViewModelImpl(lifetime.create())
        val profilingTabsController = JvmTabsViewModel(lifetime.create(), jvmsListController.connectJvmPublisher)

        return BorderLayoutPanel().apply {
            addToLeft(jvmsListController.createComponent()).apply {
                preferredSize = Dimension(150, 0)
            }
            addToCenter(profilingTabsController.createComponent())
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
