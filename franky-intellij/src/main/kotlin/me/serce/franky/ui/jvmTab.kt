package me.serce.franky.ui

import com.google.protobuf.CodedOutputStream
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import me.serce.franky.Protocol
import me.serce.franky.jvm.AttachableJVM
import me.serce.franky.jvm.JVMAttachService
import me.serce.franky.jvm.JVMSession
import me.serce.franky.ui.flame.FlameComponent
import me.serce.franky.ui.flame.FlameTree
import me.serce.franky.util.Lifetime
import me.serce.franky.util.subscribeUI
import rx.lang.kotlin.AsyncSubject
import rx.lang.kotlin.PublishSubject
import java.awt.Color
import java.awt.FlowLayout
import java.io.FileOutputStream
import javax.swing.*

class JvmTabViewModel(val lifetime: Lifetime, vm: AttachableJVM) : ViewModel {
    private val state = JvmTabState()
    private val view = JvmTabView(state)
    private val model = JvmTabConntoller(state, lifetime, vm)

    override fun createComponent() = view.createComponent()
}

private class JvmTabState {
    val connected = AsyncSubject<Boolean>()
    val isProfilingStarted = PublishSubject<Boolean>()
    val profilingResult = PublishSubject<Protocol.Response>()
}

private class JvmTabView(val state: JvmTabState) : View {
    val tabPanel = BorderLayoutPanel()

    val startButton = JButton("Start profiling")
    val stopButton = JButton().apply {
        text = "Stop profiling"
        isEnabled = false
    }
    val buttonsPanel = JPanel().apply {
        layout = FlowLayout()
        add(startButton)
        add(stopButton)
    }
    val textAres = JTextArea()

    init {
        val throbber = JTextArea("Awaiting connection")
        tabPanel.add(throbber)

        state.connected.subscribeUI {
            tabPanel.apply {
                remove(throbber)

                addToTop(buttonsPanel)
                revalidate()
            }
        }

        state.isProfilingStarted.subscribeUI {
            startButton.isEnabled = !it
            stopButton.isEnabled = it
        }

        startButton.addActionListener {
            state.isProfilingStarted.onNext(true)
        }
        stopButton.addActionListener { state.isProfilingStarted.onNext(false) }

        state.profilingResult.subscribeUI { result: Protocol.Response ->
            tabPanel.apply {
                // TODO DEV-MODE
                FileOutputStream("/home/serce/tmp/ResultData").use { fos ->
                    val out = CodedOutputStream.newInstance(fos)
                    result.writeTo(out)
                    out.flush()
                }
                val profResultViewModel = ProfResultViewModel(result)
                addToCenter(JBScrollPane(profResultViewModel.createComponent()).apply {
                    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
                    verticalScrollBar.unitIncrement = 16
                    border = BorderFactory.createLineBorder(Color.RED)
                })
            }
        }
    }

    override fun createComponent() = tabPanel
}

private class JvmTabConntoller(state: JvmTabState, lifetime: Lifetime, vm: AttachableJVM) {
    val jvmService = JVMAttachService.getInstance()
    val sessionObservable = jvmService.connect(vm)

    init {
        sessionObservable.subscribe { session ->
            lifetime += {
                session.close()
            }
            state.connected.onNext(true)
            state.connected.onCompleted()
            initSession(session, state)
        }
    }

    private fun initSession(session: JVMSession, state: JvmTabState) {
        state.isProfilingStarted.subscribeUI {
            when {
                it -> session.startProfiling()
                else -> session.stopProfiling()
            }
        }
        session.profilingResult().subscribe { response ->
            state.profilingResult.onNext(response)
        }
    }
}

