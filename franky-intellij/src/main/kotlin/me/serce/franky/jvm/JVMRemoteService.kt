package me.serce.franky.jvm

import io.netty.channel.Channel
import me.serce.franky.Protocol
import rx.lang.kotlin.AsyncSubject
import rx.subjects.AsyncSubject
import java.util.*

class JVMRemoteService {
    private val jvms: HashMap<Int, AsyncSubject<JVMRemoteInstance>> = hashMapOf()

    fun init(id: Int): AsyncSubject<JVMRemoteInstance> = AsyncSubject<JVMRemoteInstance>().apply {
        jvms[id] = this
    }

    fun setChan(id: Int, channel: Channel) = jvms[id]!!.apply {
        onNext(JVMRemoteInstance(channel))
        onCompleted()
    }

    fun result(id: Int, msg: Protocol.Response) {
        jvms[id]?.value?.onResponse?.invoke(msg)
    }
}

class JVMRemoteInstance(val chan: Channel) {
    var onResponse: ((Protocol.Response) -> Unit)? = null;

    fun send(req: Protocol.Request.RequestType) {
        chan.writeAndFlush(Protocol.Request.newBuilder()
                .setType(req)
                .build())
    }

    fun onResponse(onResponse: (Protocol.Response) -> Unit) {
        this.onResponse = onResponse
    }
}
