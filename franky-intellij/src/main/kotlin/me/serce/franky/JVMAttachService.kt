package me.serce.franky

import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor

const val port: Int = 4897

data class AttachableJVM(val id: String, val name: String)

class JVMAttachService {

    fun attachableJVMs(): List<AttachableJVM> = VirtualMachine.list()
            .map { jvm: VirtualMachineDescriptor ->
                AttachableJVM(jvm.id(), jvm.displayName())
            }

    fun connect(jvm: AttachableJVM): JVMSession = JVMSession(VirtualMachine.attach(jvm.id))
}

class JVMSession(val vm: VirtualMachine) : AutoCloseable {
    private var isRunning = false;

    init {
        vm.loadAgentPath("/home/serce/git/franky/lib/libfrankyagent.so", "$port")
    }

    fun startProfiling() {

        isRunning = true
    }

    fun endProfiling() {

        isRunning = false
    }

    override fun close() = vm.detach()
}
