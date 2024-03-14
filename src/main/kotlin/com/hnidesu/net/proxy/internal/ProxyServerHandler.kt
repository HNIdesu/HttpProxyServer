package com.hnidesu.net.proxy.internal

import com.hnidesu.log.Logger
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequestEncoder
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.ReadTimeoutHandler
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.URL
import java.nio.charset.StandardCharsets

@Sharable
internal class ProxyServerHandler(private val mOkHttpClientBuilder: OkHttpClient.Builder,
                                  private val mWorkerGroup:NioEventLoopGroup):ChannelInboundHandlerAdapter() {
    private val TAG:String="HttpRequestHandler"
    private val mLogger:Logger=Logger(TAG)
    override fun channelRead(clientCtx: ChannelHandlerContext, msg: Any) {
        val httpRequest=msg as FullHttpRequest
        when(httpRequest.method()){
            HttpMethod.CONNECT-> handleHttpsRequest(clientCtx, httpRequest)
            else-> handleHttpRequest(clientCtx, httpRequest)
        }
        super.channelRead(clientCtx, msg)
    }

    private fun handleHttpsRequest(clientCtx: ChannelHandlerContext, request: FullHttpRequest){
        clientCtx.pipeline().removeFirst()//移除http请求解码器
        clientCtx.pipeline().removeFirst()//移除http请求组合器
        clientCtx.pipeline().remove(this)
        val url=URL("https://"+request.uri())
        ConnectionManager.connect(object :ChannelInitializer<NioSocketChannel>(){
            override fun initChannel(remoteChannel: NioSocketChannel) {
                clientCtx.pipeline().addLast(object :ChannelInboundHandlerAdapter(){
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        remoteChannel.writeAndFlush(msg)
                    }
                })
                remoteChannel.pipeline().addLast(object:ChannelInboundHandlerAdapter(){
                    override fun channelActive(remoteCtx: ChannelHandlerContext) {//连接远程服务器成功
                        super.channelActive(remoteCtx)
                        clientCtx.pipeline().writeAndFlush(
                            Unpooled.wrappedBuffer("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)))//将代理请求发送到远程
                        remoteCtx.pipeline().remove(this)
                    }
                }).addLast(object:ChannelInboundHandlerAdapter(){
                    override fun channelRead(remoteCtx: ChannelHandlerContext, msg: Any) {//收到读取远程服务器响应
                        clientCtx.channel().writeAndFlush(msg)//直接发出服务器响应
                    }
                    override fun channelInactive(remoteCtx: ChannelHandlerContext) {
                        remoteCtx.channel().close()//表示Connection为close，同时关闭远程和客户端
                        clientCtx.channel().close()
                    }
                }).addLast(ReadTimeoutHandler(10))
                    .addLast(object:ChannelInboundHandlerAdapter(){
                    override fun exceptionCaught(remoteCtx: ChannelHandlerContext, cause: Throwable?) {
                        if(cause!=null){
                            when(cause){
                                is ReadTimeoutException->{
                                    remoteCtx.channel().close()
                                    clientCtx.channel().close()
                                }
                                is SocketException->{
                                    remoteCtx.channel().close()
                                    clientCtx.channel().close()
                                }
                                else->cause.printStackTrace()

                            }
                        }
                    }
                })
            }
        }, InetSocketAddress(url.host,url.port),mWorkerGroup).also {
            it.addListener{future->
                if(!future.isSuccess){
                    clientCtx.channel().close()
                    mLogger.debug("连接${it.channel().remoteAddress()}失败")
                }
            }
        }
        println(url)
    }

    private fun handleHttpRequest(clientCtx: ChannelHandlerContext, request: FullHttpRequest){//Serve as a http server
        val url=URL(request.uri())
        request.retain()//延迟发送，增加一次引用防止被释放
        ConnectionManager.connect(object :ChannelInitializer<NioSocketChannel>(){
            override fun initChannel(remoteChannel: NioSocketChannel) {
                remoteChannel.pipeline().addLast(object:ChannelInboundHandlerAdapter(){
                    override fun channelActive(remoteCtx: ChannelHandlerContext) {//连接远程服务器成功
                        super.channelActive(remoteCtx)
                        remoteCtx.pipeline().writeAndFlush(request)//将代理请求发送到远程
                        remoteCtx.pipeline().remove(this)
                    }
                }).addLast(object:ChannelInboundHandlerAdapter(){
                    override fun channelRead(remoteCtx: ChannelHandlerContext, msg: Any) {//收到读取远程服务器响应
                        clientCtx.channel().writeAndFlush(msg)//直接发出服务器响应
                    }
                    override fun channelInactive(remoteCtx: ChannelHandlerContext) {
                        remoteCtx.channel().close()//表示Connection为close，同时关闭远程和客户端
                        clientCtx.channel().close()
                    }
                }).addLast(HttpRequestEncoder())
                    .addLast(ReadTimeoutHandler(10))//如果一定时间内没有收到响应则关闭连接，主要适用于Connection为keep-alive的情况
                    .addLast(object:ChannelInboundHandlerAdapter(){
                        override fun exceptionCaught(remoteCtx: ChannelHandlerContext, cause: Throwable?) {
                            if(cause!=null){
                                when(cause){
                                    is ReadTimeoutException->{
                                        remoteCtx.channel().close()
                                        clientCtx.channel().close()
                                    }
                                    is SocketException->{
                                        remoteCtx.channel().close()
                                        clientCtx.channel().close()
                                    }
                                    else->cause.printStackTrace()

                                }
                            }
                        }
                    })
            }
        }, InetSocketAddress(url.host,if(url.port==-1)80 else url.port),mWorkerGroup).also {
            it.addListener{future->
                if(!future.isSuccess){
                    clientCtx.channel().close()
                    mLogger.debug("连接${it.channel().remoteAddress()}失败")
                }
            }
        }
        println(url)
    }

}