package com.hnidesu.net.proxy

import com.hnidesu.util.SslContextGenerator
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
import io.netty.handler.codec.http.HttpResponseEncoder
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

import javax.net.ssl.SSLContext

class ProxyServer private constructor (
    private val mAddress: InetAddress,
    private val mPort: Int,
    private val mOkHttpClient:OkHttpClient,
    private val mCertificateUtil: SslContextGenerator?=null
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
        constructor(address: InetSocketAddress):this(address.address,address.port)

        private val mBuilder:OkHttpClient.Builder = OkHttpClient.Builder()
        private var mCertificateUtil: SslContextGenerator?=null
        init {
            val ctx=SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllTrustManager), SecureRandom())
            mBuilder.sslSocketFactory(ctx.socketFactory,TrustAllTrustManager)
                .protocols(listOf(Protocol.HTTP_1_1))
                .followRedirects(false)
                .connectionPool(ConnectionPool(6,1,TimeUnit.MINUTES))
        }
        fun addInterceptor(interceptor:Interceptor):Builder{
            mBuilder.addInterceptor(interceptor)
            return this
        }

        fun setExternalProxy(proxy:Proxy):Builder{
            mBuilder.proxy(proxy)
            return this
        }

        fun initSslContext(certificate: X509Certificate, privateKey: PrivateKey):Builder{
            mCertificateUtil= SslContextGenerator(certificate,privateKey)
            return this
        }

        fun setFollowRedirect(allow:Boolean):Builder{
            mBuilder.followRedirects(allow)
            return this
        }

        fun build():ProxyServer{
            return ProxyServer(mAddress,mPort,mBuilder.build(),mCertificateUtil)
        }
    }

    fun close(){
        if(IsRunning){
            mChannelFuture?.channel()?.close()?.sync()
            mWorkerGroup?.shutdownGracefully()
            mBossGroup?.shutdownGracefully()
            mIsRunning=false
        }
    }

    fun start() {
        if(IsRunning)return
        mBossGroup = NioEventLoopGroup(1)
        mWorkerGroup = NioEventLoopGroup()
        val proxyServerHandler= ProxyServerHandler(mOkHttpClient, mCertificateUtil)
        mChannelFuture = ServerBootstrap()
            .channel(NioServerSocketChannel::class.java)
            .group(mBossGroup,mWorkerGroup)
            .childOption(ChannelOption.ALLOCATOR,PooledByteBufAllocator.DEFAULT)
            .childHandler(object:ChannelInitializer<NioSocketChannel>(){
                override fun initChannel(client: NioSocketChannel) {
                    client.pipeline()
                        .addLast(HttpRequestDecoder())
                        .addLast(HttpObjectAggregator(10*1024*1024))
                        .addLast(HttpResponseEncoder())
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