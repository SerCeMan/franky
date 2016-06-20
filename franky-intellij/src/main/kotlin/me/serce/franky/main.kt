package me.serce.franky

import com.sun.tools.attach.VirtualMachine
import io.netty.bootstrap.ServerBootstrap
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
import me.serce.franky.Protocol.Request
import me.serce.franky.Protocol.Response
import kotlin.concurrent.thread


fun main(args: Array<String>) {
    val vm = VirtualMachine.attach("6999")
    val port = 4897
    thread {
        listen(port)
    }
    Thread.sleep(200L)
}

fun listen(port: Int) {
    val bossGroup = NioEventLoopGroup(1)
    val workerGroup = NioEventLoopGroup()
    try {
        val b = ServerBootstrap()
        b.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java).childHandler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                val p = ch.pipeline()
                p.addLast(ProtobufVarint32FrameDecoder())
                p.addLast(ProtobufDecoder(Response.getDefaultInstance()))

                p.addLast(ProtobufVarint32LengthFieldPrepender())
                p.addLast(ProtobufEncoder())

                p.addLast(object : SimpleChannelInboundHandler<Response>() {
                    override fun channelActive(ctx: ChannelHandlerContext) {
                        val chan = ctx.channel()
                        chan.writeAndFlush(Request.newBuilder().apply {
                            type = Request.RequestType.START_PROFILING
                        }.build())
                        println("START PROFILING")

                        Thread.sleep(2000L)
                        chan.writeAndFlush(Request.newBuilder().apply {
                            type = Request.RequestType.STOP_PROFILING
                        }.build())
                        println("STOP PROFILING")
                    }

                    override fun channelRead0(ctx: ChannelHandlerContext, msg: Response) {
                        println("Revieved, " + msg)
                        ctx.channel().write(Request.newBuilder().apply {
                            type = Request.RequestType.DETACH
                        }.build())
                    }
                })
            }
        })

        b.bind(port).sync().channel().closeFuture().sync()
    } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}
