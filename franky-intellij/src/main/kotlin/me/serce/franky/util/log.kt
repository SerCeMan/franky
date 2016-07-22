package me.serce.franky.util

import com.intellij.openapi.diagnostic.Logger
import kotlin.reflect.companionObject

fun <T : Any> logger(clz: Class<T>): Logger = Logger.getInstance(unwrapCompanionClass(clz).name)

fun <T : Any> unwrapCompanionClass(clz: Class<T>): Class<*> = when (clz) {
    clz.enclosingClass?.kotlin?.companionObject?.java -> clz.enclosingClass
    else -> clz
}

interface Loggable

fun Loggable.logger(): Logger = logger(this.javaClass)