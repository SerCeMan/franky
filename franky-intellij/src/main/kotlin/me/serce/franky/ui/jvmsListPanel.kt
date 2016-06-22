package me.serce.franky.ui

import me.serce.franky.jvm.AttachableJVM
import me.serce.franky.jvm.JVMAttachService
import me.serce.franky.util.Lifetime
import me.serce.franky.util.subscribeUI
import rx.Observable
import rx.lang.kotlin.PublishSubject
import rx.schedulers.Schedulers
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel


interface JvmsListViewModel : HasComponent {
    val connectJvmPublisher: Observable<AttachableJVM>
}


class JvmsListViewModelImpl(val lifetime: Lifetime) : JvmsListViewModel {
    private class JvmsListState {
        val jvms = PublishSubject<List<AttachableJVM>>()
        val jvmPublisher = PublishSubject<AttachableJVM>()
    }


    private val state = JvmsListState()
    private val view = JvmsListView(state)
    private val model = JvmsController(lifetime, state)

    override val connectJvmPublisher = state.jvmPublisher

    override fun createComponent() = view.createComponent()

    private class JvmsController(val lifetime: Lifetime, val state: JvmsListState) {
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

    private class JvmsListView(val state: JvmsListState) : View {
        val listModel = DefaultListModel<AttachableJVM>()
        val jvmsList = JList<AttachableJVM>().apply {
            model = listModel
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        state.jvmPublisher.onNext(selectedValue)
                    }
                }
            })
        }


        init {
            state.jvms.subscribeUI { jvms ->
                if (jvms != listModel.elements().toList()) {
                    listModel.removeAllElements()
                    jvms?.forEach { listModel.addElement(it) }
                }
            }

            jvmsList.cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component? {
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                        value as AttachableJVM?
                        if (value != null) {
                            text = "${value.id} ${value.name.substring(0, Math.min(value.name.length, 20))}"
                        }
                    }
                }
            }
        }

        override fun createComponent() = JPanel().apply {
            add(jvmsList)
        }
    }
}
