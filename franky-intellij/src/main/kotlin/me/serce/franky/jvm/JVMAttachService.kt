package me.serce.franky.jvm

import com.google.protobuf.CodedInputStream
import com.intellij.openapi.components.ServiceManager
import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor
import me.serce.franky.FRANKY_PORT
import me.serce.franky.Protocol
import me.serce.franky.Protocol.Request.RequestType.START_PROFILING
import me.serce.franky.Protocol.Request.RequestType.STOP_PROFILING
import rx.Observable
import rx.schedulers.Schedulers
import java.io.FileInputStream
import kotlin.concurrent.thread

data class AttachableJVM(val id: String, val name: String) : Comparable<AttachableJVM> {
    override fun compareTo(other: AttachableJVM) = id.compareTo(other.id)
}

class JVMAttachService(val jvmRemoteService: JVMRemoteService) {
    companion object {
        fun getInstance() = ServiceManager.getService(JVMAttachService::class.java)
    }

    fun attachableJVMs(): List<AttachableJVM> = VirtualMachine.list()
            .map { jvm: VirtualMachineDescriptor ->
                AttachableJVM(jvm.id(), jvm.displayName())
            }

    fun connect(jvm: AttachableJVM): Observable<JVMSession> {
        val channelObs = jvmRemoteService.init(jvm.id.toInt())
        return Observable
                .fromCallable {
                    VirtualMachine.attach(jvm.id)
                }
                .map { vm ->
                    thread(isDaemon = true, name = "VM Attach Thread pid=${vm.id()}") {
                        vm.loadAgentPath("/home/serce/git/franky/lib/libfrankyagent.so", "$FRANKY_PORT")
                    }
                    vm
                }
                .zipWith(channelObs, { removeVm, vm -> Pair(vm, removeVm) })
                .subscribeOn(Schedulers.io())
                .map { p -> JVMSession(p.first, p.second) }
    }
}

class JVMSession(private val remoteJVM: JVMRemoteInstance,
                 private val vm: VirtualMachine) : AutoCloseable {
    private var isRunning = false;

    // todo DEV-MODE
    //    fun startProfiling() {
    //        remoteJVM.send(START_PROFILING)
    //        isRunning = true
    //    }
    //
    //    fun stopProfiling() {
    //        remoteJVM.send(STOP_PROFILING)
    //        isRunning = false
    //    }

    fun startProfiling() {
        isRunning = true
    }

    fun stopProfiling() {
        isRunning = false
        profilingResult().onNext(Protocol.Response.parseFrom(CodedInputStream.newInstance(FileInputStream("/home/serce/tmp/ResultData"))))
    }


    fun profilingResult() = remoteJVM.response

    override fun close() = vm.detach()
}
