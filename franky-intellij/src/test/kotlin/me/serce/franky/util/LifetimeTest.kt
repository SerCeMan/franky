package me.serce.franky.util

import org.junit.Assert.*
import org.junit.Test

class LifetimeTest {

    @Test
    fun testLifetimes() {
        val def = Lifetime.create(Lifetime.Eternal)

        val str = StringBuilder()
        def += { str.append("World") }
        def += { str.append("Hello") }
        val def2 = def.create()
        def2 += { str.append("1") }
        def2 += { str.append("2") }
        def.terminate()

        assertTrue(str.toString() == "21HelloWorld")
    }
}