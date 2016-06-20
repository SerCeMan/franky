package me.serce.franky

import io.netty.channel.Channel
import java.util.*

class JVMRemoteService {
    private val jvms: HashMap<Int, JVMRemoteInstance> = hashMapOf()

    fun init(id: Int): JVMRemoteInstance = JVMRemoteInstance().apply {
        jvms[id] = this
    }

    fun setChan(id: Int, channel: Channel) {
        jvms[id]?.chan = channel;
    }

    fun result(id: Int, msg: Protocol.Response) {
        jvms[id]?.onResponse?.invoke(msg)
    }
}

class JVMRemoteInstance() {
    var chan: Channel? = null
    var onResponse: ((Protocol.Response) -> Unit)? = null;

    fun send(req: Protocol.Request.RequestType) {
        chan!!.writeAndFlush(Protocol.Request.newBuilder()
                .setType(req)
                .build())
    }

    fun onResponse(onResponse: (Protocol.Response) -> Unit) {
        this.onResponse = onResponse
    }
}
