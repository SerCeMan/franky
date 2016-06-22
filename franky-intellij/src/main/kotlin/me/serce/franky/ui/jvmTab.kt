package me.serce.franky.ui

import me.serce.franky.jvm.AttachableJVM
import me.serce.franky.jvm.JVMAttachService
import me.serce.franky.jvm.JVMSession
import me.serce.franky.Protocol
import me.serce.franky.util.Lifetime
import me.serce.franky.util.subscribeUI
import rx.lang.kotlin.AsyncSubject
import rx.lang.kotlin.PublishSubject
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

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
    val tabPanel = JPanel()

    val startButton = JButton("Start profiling")
    val stopButton = JButton().apply {
        text = "Stop profiling"
        isEnabled = false
    }
    val textAres = JTextArea()

    init {
        val throbber = JTextArea("Awaiting connection")
        tabPanel.add(throbber)

        state.connected.subscribeUI {
            tabPanel.apply {
                remove(throbber)

                add(startButton)
                add(stopButton)
                add(textAres)

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

        state.profilingResult.subscribeUI {
            textAres.append(it.toString())
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
        session.addResultListener { response ->
            state.profilingResult.onNext(response)
        }
    }
}

