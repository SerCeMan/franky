package me.serce.franky

import com.intellij.openapi.components.ServiceManager
import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor
import me.serce.franky.Protocol.Request.RequestType.START_PROFILING
import me.serce.franky.Protocol.Request.RequestType.STOP_PROFILING
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

    fun connect(jvm: AttachableJVM): JVMSession =
            JVMSession(jvmRemoteService.init(jvm.id.toInt()), VirtualMachine.attach(jvm.id))
}

class JVMSession(val remoteJVM: JVMRemoteInstance, val vm: VirtualMachine) : AutoCloseable {
    private var isRunning = false;

    init {
        thread {
            vm.loadAgentPath("/home/serce/git/franky/lib/libfrankyagent.so", "$FRANKY_PORT")
        }
    }

    fun startProfiling() {
        remoteJVM.send(START_PROFILING)
        isRunning = true
    }

    fun stopProfiling() {
        remoteJVM.send(STOP_PROFILING)
        isRunning = false
    }

    fun addResultListener(function: (Protocol.Response) -> Unit) {
        remoteJVM.onResponse(function)
    }

    override fun close() = vm.detach()
}
