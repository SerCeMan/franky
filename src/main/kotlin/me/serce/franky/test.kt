package me.serce.franky

import jnr.ffi.LibraryLoader
import java.lang.management.ManagementFactory
import java.math.BigInteger

/**
 * Created by serce on 12.06.16.
 */
fun main(args: Array<String>) {
    println(ManagementFactory.getRuntimeMXBean().getName())
    while (true) {
        print(fib(BigInteger.valueOf(42)))
    }
}

fun fib(i: BigInteger): BigInteger {
    if (i == BigInteger.ZERO || i == BigInteger.ONE) {
        return BigInteger.ONE
    }
    return fib(i - BigInteger.ONE) + fib(i - BigInteger.valueOf(2))
}
