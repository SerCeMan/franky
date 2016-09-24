package me.serce.franky.jvm

import com.intellij.openapi.components.ServiceManager
import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor
import me.serce.franky.FrankyComponent
import me.serce.franky.Protocol.Request.RequestType.START_PROFILING
import me.serce.franky.Protocol.Request.RequestType.STOP_PROFILING
import me.serce.franky.util.Loggable
import me.serce.franky.util.ensureLibattach
import me.serce.franky.util.logger
import rx.Observable
import rx.schedulers.Schedulers
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.concurrent.thread

data class AttachableJVM(val id: String, val name: String) : Comparable<AttachableJVM> {
    override fun compareTo(other: AttachableJVM) = id.compareTo(other.id)
}

class JVMAttachService(val jvmRemoteService: JVMRemoteService, val frankyComponent: FrankyComponent) {
    companion object : Loggable {
        val LOG = logger()
        fun getInstance() = ServiceManager.getService(JVMAttachService::class.java)
    }

    fun attachableJVMs(): List<AttachableJVM> = VirtualMachine.list().map { jvm: VirtualMachineDescriptor ->
        AttachableJVM(jvm.id(), jvm.displayName())
    }

    fun connect(jvm: AttachableJVM): Observable<JVMSession> {
        val channelObs = jvmRemoteService.init(jvm.id.toInt())

        return Observable
                .fromCallable {
                    try {
                        ensureLibattach()
                        VirtualMachine.attach(jvm.id)
                    } catch (e: Throwable) {
                        if (e is UnsatisfiedLinkError) {
                            throw RuntimeException("Unable connect VM. Please add libattach.so library to libpath", e)
                        }
                        throw RuntimeException("Unable to connect to ${jvm.id}", e)
                    }
                }
                .doOnNext { vm ->
                    val pid = vm.id()
                    thread(isDaemon = true, name = "VM Attach Thread pid=$pid") {
                        LOG.info("Attaching Franky JVM agent to pid=$pid")
                        try {
                            val frankyPath = Files.createTempFile("libfrankyagent_tmp", ".so").apply {
                                toFile().deleteOnExit()
                            }
                            val frankyResource = FrankyComponent::class.java.classLoader.getResource("libfrankyagent.so").openStream()
                            Files.copy(frankyResource, frankyPath, REPLACE_EXISTING)
                            vm.loadAgentPath(frankyPath.toAbsolutePath().toString(), "${frankyComponent.FRANKY_PORT}")
                        } catch (t: Throwable) {
                            LOG.error("JVM connection had crashed", t)
                        }
                        LOG.info("Franky JVM agent detached from pid=$pid")
                    }
                }
                .zipWith(channelObs, { removeVm, vm -> Pair(vm, removeVm) })
                .subscribeOn(Schedulers.io())
                .map { p -> JVMSession(p.first, p.second) }
    }
}

//fun main(args: Array<String>) {
//    val vm = VirtualMachine.attach("25034")
//    thread(isDaemon = true, name = "VM Attach Thread pid=${vm.id()}") {
//        vm.loadAgentPath("/home/serce/git/franky/lib/libfrankyagent.so", "$FRANKY_PORT")
//    }
//    readLine()
//}

class JVMSession(private val remoteJVM: JVMRemoteInstance,
                 private val vm: VirtualMachine) : AutoCloseable, Loggable {
    private var isRunning = false
    private val LOG = logger()


    fun startProfiling() {
        LOG.info("Starting profiling session ${vm.id()}")
        remoteJVM.send(START_PROFILING)
        isRunning = true
    }

    fun stopProfiling() {
        LOG.info("Stopping profiling session ${vm.id()}")
        remoteJVM.send(STOP_PROFILING)
        isRunning = false
    }

    fun profilingResult() = remoteJVM.response

    override fun close() {
        LOG.info("Closing JVM session")
        vm.detach()
        remoteJVM.close()
    }
}
