package me.serce.franky.ui

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBTabsPaneImpl
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.ui.components.BorderLayoutPanel
import me.serce.franky.FrankyComponent
import me.serce.franky.jvm.AttachableJVM
import me.serce.franky.util.Lifetime
import me.serce.franky.util.create
import me.serce.franky.util.toDisposable
import rx.Observable
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingConstants

class FrankyToolWindowFactory(val frankyComponent: FrankyComponent) : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val lifetime = frankyComponent.rootLifetime.create()
        val frankyPanelController = FrankyPanelController(project, lifetime)
        val frankyPanel = frankyPanelController.createComponent()

        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(frankyPanel, null, true)
        contentManager.addContent(content)
        Disposer.register(project, lifetime.toDisposable())
    }
}

class FrankyPanelController(val project: Project, val lifetime: Lifetime) : ViewModel {

    override fun createComponent(): JComponent {
        val jvmsListController: JvmsListViewModel = JvmsListViewModelImpl(lifetime.create())
        val profilingTabsController = JvmTabsViewModel(project, lifetime.create(), jvmsListController.connectJvmPublisher)

        return BorderLayoutPanel().apply {
            addToLeft(jvmsListController.createComponent()).apply {
                preferredSize = Dimension(150, 0)
            }
            addToCenter(profilingTabsController.createComponent())
        }
    }
}

class JvmTabsViewModel(val project: Project, val lifetime: Lifetime, jvmPublisher: Observable<AttachableJVM>) {
    val view = JvmTabsView(project, lifetime)

    init {
        jvmPublisher.subscribe { vm ->
            val lifetime = lifetime.create()
            val tabController = JvmTabViewModel(lifetime, vm)
            view.addTab(vm.id, tabController.createComponent(), lifetime)
        }
    }

    fun createComponent() = view.createComponent()

    class JvmTabsView(project: Project, lifetime: Lifetime) : View {
        val tabPane = JBTabsPaneImpl(project, SwingConstants.TOP, lifetime.toDisposable())
        val tabs: JBTabs = tabPane.tabs

        fun addTab(name: String, comp: JComponent, lifetime: Lifetime) {
            tabs.addTab(TabInfo(comp) {
                text = name
                val actionGroup = DefaultActionGroup().apply {
                    add(CloseAction {
                        tabs.removeTab(this@TabInfo)
                        lifetime.terminate()
                    })
                }
                setTabLabelActions(actionGroup, "ProfilerTab")
            })
        }

        override fun createComponent() = tabPane.component
    }
}