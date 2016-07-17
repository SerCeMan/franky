package me.serce.franky.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.junit.Assert.assertTrue
import org.junit.Test

class LifetimeTest {

    @Test
    fun toDisposableDisposerTest() {
        val life = Lifetime.create(Lifetime.Eternal)

        var disposed = false;
        life += {
            disposed = true
        }
        val disposable = life.toDisposable()
        Disposer.dispose(disposable)
        assertTrue(disposed)
    }

    @Test
    fun toDisposableLifetimeTest() {
        val life = Lifetime.create(Lifetime.Eternal)

        var disposed = false
        val disposable = life.toDisposable()
        Disposer.register(disposable, Disposable {
            disposed = true
        })
        life.terminate()
        assertTrue(disposed)
    }

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