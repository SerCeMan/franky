package me.serce.franky.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer


class Lifetime internal constructor(eternal: Boolean = false) {
    companion object {
        val Eternal: Lifetime = Lifetime(true)
        fun create(vararg parents: Lifetime): Lifetime {
            val res = Lifetime()
            for (parent in parents) {
                parent.addNested(res)
            }
            return res
        }
    }

    val isEternal: Boolean = eternal
    @Volatile
    var isTerminated: Boolean = false
        private set

    private val actions = arrayListOf<() -> Unit>()

    operator fun plusAssign(action: () -> Unit) = add(action)

    fun terminate() {
        isTerminated = true;
        val actionsCopy = actions
        actionsCopy.reversed().forEach {
            it()
        }
        actions.clear()
    }

    private fun add(action: () -> Unit) {
        if (isTerminated) {
            throw IllegalStateException("Already terminated")
        }
        actions.add (action)
    }

    internal fun addNested(def: Lifetime) {
        if (def.isTerminated) {
            return
        }
        val action = { def.terminate() }
        add(action)
        def.add({ actions.remove(action) })
    }
}

fun Lifetime.create(): Lifetime = Lifetime.create(this)

fun Lifetime.toDisposable(): Disposable {
    val disposable = Disposer.newDisposable()
    this += { Disposer.dispose(disposable) }
    return disposable
}
