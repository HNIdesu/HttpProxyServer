package com.hnidesu.net.proxy

import com.hnidesu.log.Logger
import com.hnidesu.net.proxy.internal.ProxyServerHandler
import com.hnidesu.net.proxy.internal.TrustAllTrustManager
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.InetAddress
import java.net.Proxy
import java.security.SecureRandom

import javax.net.ssl.SSLContext

class ProxyServer private constructor (
    private val mAddress: InetAddress,
    private val mPort: Int,
    private val mSSLContext:SSLContext?,
    private val mOkHttpClientBuilder:OkHttpClient.Builder
){
    val TAG:String="ProxyServer"
    private val mLogger=Logger(TAG)
    private var mIsRunning:Boolean=false
    private var mChannelFuture:ChannelFuture?=null
    val IsRunning:Boolean
        get() = mIsRunning
    val Port:Int
        get() = mPort
    val Address:InetAddress
        get() = mAddress

    private var mBossGroup:NioEventLoopGroup?=null
    private var mWorkerGroup:NioEventLoopGroup?=null

    class Builder(private val mAddress: InetAddress,private val mPort:Int){
        constructor(port: Int):this(InetAddress.getByName("0.0.0.0"),port)

        private val mBuilder:OkHttpClient.Builder = OkHttpClient.Builder()
        private var mSSLContext:SSLContext?=null
        init {
            val ctx=SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllTrustManager), SecureRandom())
            mBuilder.sslSocketFactory(ctx.socketFactory,TrustAllTrustManager)
                .protocols(listOf(Protocol.HTTP_1_1))
                .followRedirects(false)
        }
        fun addInterceptor(interceptor:Interceptor):Builder{
            mBuilder.addInterceptor(interceptor)
            return this
        }

        fun setExternalProxy(proxy:Proxy):Builder{
            mBuilder.proxy(proxy)
            return this
        }

        fun setSSLContext(ctx:SSLContext):Builder{
            mSSLContext=ctx
            return this
        }

        fun setFollowRedirect(allow:Boolean):Builder{
            mBuilder.followRedirects(allow)
            return this
        }

        fun build():ProxyServer{
            return ProxyServer(mAddress,mPort,mSSLContext,mBuilder)
        }
    }

    fun close(){
        if(IsRunning){
            mChannelFuture?.channel()?.closeFuture()?.sync()
            mWorkerGroup?.shutdownGracefully()
            mBossGroup?.shutdownGracefully()
            mIsRunning=false
        }
    }

    fun start() {
        if(IsRunning)return
        mBossGroup = NioEventLoopGroup(1)
        mWorkerGroup = NioEventLoopGroup()
        val proxyServerHandler= ProxyServerHandler(mOkHttpClientBuilder,mWorkerGroup!!)
        mChannelFuture = ServerBootstrap()
            .channel(NioServerSocketChannel::class.java)
            .group(mBossGroup,mWorkerGroup)
            .childOption(ChannelOption.ALLOCATOR,PooledByteBufAllocator.DEFAULT)
            .childHandler(object:ChannelInitializer<NioSocketChannel>(){
                override fun initChannel(client: NioSocketChannel) {
                    client.pipeline()
                        .addLast(HttpRequestDecoder())
                        .addLast(HttpObjectAggregator(10*1024*1024))
                        .addLast(proxyServerHandler)
                        .addLast(object:ChannelInboundHandlerAdapter(){
                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
                                cause?.printStackTrace()
                            }
                        })
                }
            }).bind(mAddress,mPort).sync()
        mIsRunning=true
    }



}