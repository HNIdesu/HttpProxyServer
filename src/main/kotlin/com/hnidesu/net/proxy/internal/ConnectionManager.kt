package com.hnidesu.net.proxy.internal

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.InetSocketAddress

internal object ConnectionManager {
    fun connect(initializer: ChannelInitializer<NioSocketChannel>,remote:InetSocketAddress,workerGroup:NioEventLoopGroup):ChannelFuture{
        return Bootstrap()
            .channel(NioSocketChannel::class.java)
            .group(workerGroup)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,8000)
            .handler(initializer)
            .connect(remote)
    }
}