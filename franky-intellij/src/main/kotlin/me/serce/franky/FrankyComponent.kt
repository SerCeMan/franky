package me.serce.franky

import com.intellij.openapi.components.ApplicationComponent
import com.sun.tools.attach.VirtualMachine
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import me.serce.franky.Protocol.Response.ResponseType
import me.serce.franky.util.Lifetime
import org.jetbrains.io.addChannelListener
import kotlin.concurrent.thread

const val FRANKY_PORT: Int = 4897;

class FrankyComponent(val jvmRemoteService: JVMRemoteService) : ApplicationComponent {
    val lifetime = Lifetime()

    private @Volatile var ch: Channel? = null

    init {
        lifetime += {
            ch?.close()
        }
    }


    override fun getComponentName(): String = "Franky Profiler"
    override fun initComponent() = listen(FRANKY_PORT)

    private fun listen(port: Int) {
        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()

        val b = ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val p = ch.pipeline()
                        p.addLast(ProtobufVarint32FrameDecoder())
                        p.addLast(ProtobufDecoder(Protocol.Response.getDefaultInstance()))

                        p.addLast(ProtobufVarint32LengthFieldPrepender())
                        p.addLast(ProtobufEncoder())

                        p.addLast(object : SimpleChannelInboundHandler<Protocol.Response>() {
                            override fun channelActive(ctx: ChannelHandlerContext) {
                                println("CHAN CONNECTED")
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: Protocol.Response) {
                                println("Revieved, " + msg)
                                when (msg.type) {
                                    ResponseType.INIT -> jvmRemoteService.setChan(msg.id, ctx.channel())
                                    ResponseType.PROF_INFO -> jvmRemoteService.result(msg.id, msg);
                                    else -> throw RuntimeException("Unknown message $msg")
                                }
                            }
                        })
                    }
                })

        b.bind(port).addChannelListener {
            val channel = it.channel()
            ch = channel

            channel.closeFuture().addChannelListener {
                bossGroup.shutdownGracefully()
                workerGroup.shutdownGracefully()
            }
        }
    }


    override fun disposeComponent() = Unit
}