package me.serce.franky.util

import com.google.common.base.Preconditions


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
    @Volatile var isTerminated: Boolean = false
        private set

    private val actions = arrayListOf<() -> Unit>()

    operator fun plusAssign(action: () -> Unit) {
        add(action)
    }

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

    //short-living lifetimes could explode action termination queue, so we need to drop them after termination
    internal fun addNested(def: Lifetime) {
        if (def.isTerminated) return;

        val action = { def.terminate() }
        add(action)
        def.add({ actions.remove(action) })
    }
}

fun Lifetime.create(): Lifetime = Lifetime.create(this)

fun main(args: Array<String>) {
    val str = StringBuilder()

    val def = Lifetime.create(Lifetime.Eternal)

    def += { str.append("World") }
    def += { str.append("Hello") }
    val def2 = def.create()
    def2 += { str.append("1") }
    def2 += { str.append("2") }
    def.terminate()

    fun check(ex: Boolean) = if (!ex) throw RuntimeException("ERROR") else Unit;

    check(str.toString() == "21HelloWorld")
}