package me.serce.franky

import com.sun.tools.attach.VirtualMachine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.concurrent.thread


interface Profiler {
    fun start(interval: Int)
    fun stop()
    fun getSamples(): Int
    fun dumpTraces(maxTraces: Int): String
    fun dumpMethods(): String
}


fun main(args: Array<String>) {
    val vm = VirtualMachine.attach("17812")
    thread {
        listen(4897)
    }
    Thread.sleep(100L)
    vm.loadAgentPath("/home/serce/git/franky/libasyncProfiler.so", "")
    Thread.sleep(3000L)
}

fun listen(i: Int) {
    val serverSocket = ServerSocket(i);
    val socket = serverSocket.accept()
    val bufferedReader = BufferedReader(InputStreamReader(socket.inputStream));

    val inputLine = bufferedReader.readLine();
    println(inputLine)
}
