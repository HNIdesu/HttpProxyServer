package com.hnidesu.net.proxy.internal

import com.hnidesu.util.SslContextGenerator
import com.hnidesu.log.Logger
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

@Sharable
internal class ProxyServerHandler(
    private val mOkHttpClient: OkHttpClient,
    private val mCertificateUtil: SslContextGenerator?=null
):ChannelInboundHandlerAdapter() {
    companion object{
        private const val TAG:String="HttpRequestHandler"
    }

    private val mLogger:Logger=Logger(TAG)
    private val mHttpMethodRequireBody= setOf(HttpMethod.POST,HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
    override fun channelRead(clientCtx: ChannelHandlerContext, msg: Any) {
        val httpRequest=msg as FullHttpRequest
        when(httpRequest.method()){
            HttpMethod.CONNECT-> handleHttpsRequest(clientCtx, httpRequest)
            else-> handleHttpRequest(clientCtx, httpRequest)
        }
        super.channelRead(clientCtx, msg)
    }

    private fun handleHttpsRequest(clientCtx: ChannelHandlerContext, request: FullHttpRequest){
        if(mCertificateUtil==null){
            clientCtx.channel().close()
            return
        }
        clientCtx.writeAndFlush(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)) //响应客户端
        val host=request.uri().let {
            if(it.contains(":"))
                it.substring(0,it.indexOf(':'))
            else
                it
        }
        clientCtx.pipeline().addFirst(mCertificateUtil.generateSslContext(host).newHandler(clientCtx.alloc()))//用于加解密https流量，将https请求转换为http请求
    }

    private fun handleHttpRequest(clientCtx: ChannelHandlerContext, request: FullHttpRequest){
        val url=try {
            URL(request.uri())
        }catch (ex:MalformedURLException){
            val path=request.uri()
            val host=request.headers()["host"]
            URL("https://$host$path")
        }
        mOkHttpClient.newCall(Request.Builder().apply {
            url(url)
            request.headers().forEach {
                header(it.key,it.value)
            }
            if(!request.headers().contains("accept-encoding"))
                header("accept-encoding","identity")
            val requestBody:RequestBody?
            if(request.method() in mHttpMethodRequireBody)
            {
                val toRead=request.content().readableBytes()
                val byteArray=ByteArray(toRead)
                request.content().readBytes(byteArray)
                requestBody=byteArray.toRequestBody()
            }else
                requestBody=null
            method(request.method().name(),requestBody)
        }.build()).enqueue(object:Callback{
            override fun onFailure(call: Call, e: IOException) {
                clientCtx.writeAndFlush(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))
                clientCtx.channel().close()
                mLogger.debug("fail to connect to ${url}")
            }
            override fun onResponse(call: Call, response: Response) {
                val httpResponse=DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.valueOf(response.code))
                httpResponse.headers().also {
                    response.headers.forEach{header->
                        it[header.first]=header.second
                    }
                }
                clientCtx.pipeline().writeAndFlush(httpResponse)
                response.body.use {body->
                    if(body!=null){
                        HttpOutputStream(clientCtx.channel()).use {
                            val source=body.source()
                            while (!source.exhausted())
                                it.write(source)
                        }
                    }
                }
                response.headers["connection"].also {
                    if(it!=null&&it.lowercase()=="close"){
                        clientCtx.channel().close()
                    }
                }
            }
        })
    }

}