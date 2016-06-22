package me.serce.franky.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import me.serce.franky.AttachableJVM
import me.serce.franky.JVMAttachService
import me.serce.franky.Protocol
import me.serce.franky.util.Lifetime
import me.serce.franky.util.create
import me.serce.franky.util.subscribeUI
import rx.Observable
import rx.lang.kotlin.AsyncSubject
import rx.lang.kotlin.PublishSubject
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.awt.Component
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.*

interface HasComponent {
    fun createComponent(): JComponent
}

interface Controller : HasComponent
interface View : HasComponent


class FrankyToolWindowFactory() : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val frankyPanelController = FrankyPanelController()
        val frankyPanel = frankyPanelController.createComponent()
        toolWindow.component.add(frankyPanel)
    }
}

class FrankyPanelController() : Controller {

    val lifetime: Lifetime = Lifetime()

    override fun createComponent(): JComponent {
        val jvmsListController: JvmsListViewModel = JvmsListViewController(lifetime.create())
        val profilingTabsController = JvmTabsController(lifetime.create(), jvmsListController.connectJvmPublisher)

        return BorderLayoutPanel().apply {
            addToLeft(jvmsListController.createComponent()).apply {
                preferredSize = Dimension(150, 0)
            }
            addToRight(profilingTabsController.createComponent())
        }
    }
}

interface JvmsListViewModel : HasComponent {
    val connectJvmPublisher: Observable<AttachableJVM>
}

class JvmsListViewController(val lifetime: Lifetime) : JvmsListViewModel {
    class JvmsListState {
        val jvms = PublishSubject<List<AttachableJVM>>()
        val selected = PublishSubject<AttachableJVM?>()
    }


    override val connectJvmPublisher = PublishSubject<AttachableJVM>()

    val state = JvmsListState()
    val view = JvmsListView(state, connectJvmPublisher)
    val model = JvmsModel(lifetime, state)

    override fun createComponent() = view.createComponent()


    class JvmsModel(val lifetime: Lifetime, val state: JvmsListState) {
        val attachService = JVMAttachService.getInstance()

        init {
            // todo lifetime
            lifetime
            Observable.interval(0, 5, TimeUnit.SECONDS, Schedulers.io())
                    .map { attachService.attachableJVMs() }
                    .subscribe {
                        state.jvms.onNext(it)
                    }
        }
    }

    class JvmsListView(val state: JvmsListState, val jvmPublisher: PublishSubject<AttachableJVM>) : View {
        val jvmsList = JComboBox<AttachableJVM>()

        val connectButton = JButton().apply {
            text = "Connect"
            isEnabled = false

            addActionListener {
                val selected = jvmsList.selectedItem as AttachableJVM?
                if (selected != null) {
                    jvmPublisher.onNext(selected)
                }
            }
        }

        init {
            state.jvms.subscribeUI { jvms ->
                jvmsList.removeAllItems()
                jvms?.forEach { jvmsList.addItem(it) }
            }
            state.selected.subscribeUI {
                connectButton.isEnabled = it != null
            }

            jvmsList.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component? {
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                        value as AttachableJVM?
                        if (value != null) {
                            text = "${value.id} ${value.name.substring(0, Math.min(value.name.length, 20))}"
                        }
                    }
                }
            }

            jvmsList.addItemListener {
                state.selected.onNext(jvmsList.selectedItem as AttachableJVM?)
            }
        }

        override fun createComponent() = JPanel().apply {
            add(jvmsList)
            add(connectButton)
        }
    }
}


class JvmTabsController(val lifetime: Lifetime, jvmPublisher: Observable<AttachableJVM>) {
    val view = JvmTabsView()

    init {
        jvmPublisher.subscribe { vm ->
            val tabController = JvmTabController(lifetime.create(), vm)
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

class JvmTabController(lifetime: Lifetime, vm: AttachableJVM) : Controller {
    class JvmTabState {
        val isProfilingStarted = PublishSubject<Boolean>()
        val profilingResult = PublishSubject<Protocol.Response>()
    }

    val state = JvmTabState()
    val view = JvmTabView(state)
    val model = JvmTabModel(state, vm)

    class JvmTabModel(state: JvmTabState, vm: AttachableJVM) {
        val jvmService = JVMAttachService.getInstance()
        val session = jvmService.connect(vm)

        init {
            state.isProfilingStarted.subscribeUI {
                when {
                    it -> session.startProfiling()
                    else -> session.stopProfiling()
                }
            }
            session.addResultListener { response ->
                state.profilingResult.onNext(response)
            }
        }
    }


    class JvmTabView(val state: JvmTabState) : View {
        val startButton = JButton("Start profiling")
        val stopButton = JButton().apply {
            text = "Stop profiling"
            isEnabled = false
        }
        val textAres = JTextArea()

        init {
            state.isProfilingStarted.subscribeUI {
                startButton.isEnabled = !it
                stopButton.isEnabled = it
            }

            startButton.addActionListener {
                state.isProfilingStarted.onNext(true)
            }
            stopButton.addActionListener { state.isProfilingStarted.onNext(false) }

            state.profilingResult.subscribeUI {
                textAres.append(it.toString())
            }
        }

        override fun createComponent() = JPanel().apply {
            add(startButton)
            add(stopButton)
            add(textAres)
        }
    }

    override fun createComponent() = view.createComponent()
}
