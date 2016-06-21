package me.serce.franky.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.ui.UIUtil
import me.serce.franky.AttachableJVM
import me.serce.franky.JVMAttachService
import me.serce.franky.Protocol.Response
import me.serce.franky.util.Lifetime
import me.serce.franky.util.create
import me.serce.franky.util.subscribeUI
import rx.Observable
import rx.Single
import rx.functions.Action1
import rx.lang.kotlin.AsyncSubject
import rx.lang.kotlin.PublishSubject
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.awt.Component
import java.awt.FlowLayout
import java.util.concurrent.TimeUnit
import javax.swing.*

interface HasComponent {
    fun createComponent(): JComponent
}

interface ViewModel : HasComponent
interface View : HasComponent


class FrankyToolWindowFactory(val jvmAttachService: JVMAttachService) : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val frankyPanelController = FrankyPanelController(jvmAttachService)
        val frankyPanel = frankyPanelController.createComponent()
        toolWindow.component.add(frankyPanel)
    }

    private fun createFrankyPanel(): JPanel {
        val jvmsList = JComboBox<AttachableJVM>().apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component? {
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                        value as AttachableJVM
                        text = "${value.id} ${value.name.substring(0, Math.min(value.name.length, 20))}"
                    }
                }
            }

            addActionListener {
                println("Hello! ${selectedItem}")
            }
        }

        val refreshButton = JButton().apply {
            text = "Refresh"
            addActionListener {
                refreshJvmsList(jvmsList)
            }
        }

        val connectButton = JButton().apply {
            text = "Connect"
        }

        val jmvsPanel = JPanel().apply {
            layout = FlowLayout()

            add(refreshButton)
            add(jvmsList)
            add(connectButton)
        }

        val frankyPanel = JPanel()

        frankyPanel.add(jmvsPanel)


        connectButton.addActionListener {
            val session = jvmAttachService.connect(jvmsList.selectedItem as AttachableJVM)

            val resultArea = JTextArea()
            session.addResultListener { res: Response ->
                println("RESULT $res")
                resultArea.text = res.toString()
            }


            val startProfilingButton = JButton("Start profiling")
            startProfilingButton.addActionListener {
                session.startProfiling()
            }

            val stopProfilingButton = JButton("Stop profiling")
            stopProfilingButton.addActionListener {
                session.stopProfiling()
            }

            frankyPanel.add(JPanel().apply {
                layout = FlowLayout()

                add(startProfilingButton)
                add(stopProfilingButton)
                add(resultArea)
            })
        }

        refreshJvmsList(jvmsList)
        return frankyPanel
    }

    private fun refreshJvmsList(jvmsList: JComboBox<AttachableJVM>) {
        jvmsList.removeAllItems();
        jvmAttachService.attachableJVMs().forEach { jvmsList.addItem(it) }
    }
}

class FrankyPanelController(val jvmAttachService: JVMAttachService) {

    val lifetime: Lifetime = Lifetime()

    fun createComponent(): JComponent {
        val jvmsListController: JvmsListViewModel = JvmsListViewModelImpl(lifetime.create())
        val profilingTabsController = ProfilingTabsController(lifetime.create(), jvmsListController.connectJvmPublisher)

        return JPanel().apply {
            add(jvmsListController.createComponent())
//            add(profilingTabsController.createComponent())
        }
    }
}

interface JvmsListViewModel : HasComponent {
    val connectJvmPublisher: Observable<AttachableJVM>
}

class JvmsListViewModelImpl(val lifetime: Lifetime) : JvmsListViewModel {
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


class ProfilingTabsController(lifetime: Lifetime, jvmPublisher: Observable<AttachableJVM>) {
    init {
        jvmPublisher.subscribe {
            println("CONNECT $it")
        }
    }

    fun createComponent(): Component {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
