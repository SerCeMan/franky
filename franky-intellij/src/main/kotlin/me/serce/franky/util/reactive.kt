package me.serce.franky.util

import com.intellij.util.ui.UIUtil
import rx.Observable

fun <T> Observable<T>.subscribeUI(onNext: (T) -> Unit) = subscribe { value ->
    UIUtil.invokeLaterIfNeeded {
        onNext(value)
    }
}