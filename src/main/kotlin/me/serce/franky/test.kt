package me.serce.franky

import java.lang.management.ManagementFactory
import java.math.BigInteger

fun main(args: Array<String>) {
    println(ManagementFactory.getRuntimeMXBean().name)
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
