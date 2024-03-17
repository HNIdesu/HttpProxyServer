package com.hnidesu.net.proxy.internal

import X509CertificateGenerator
import com.hnidesu.log.Logger
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets

@Sharable
internal class ProxyServerHandler(
    private val mOkHttpClientBuilder: OkHttpClient.Builder,
    private val mCertificateUtil:X509CertificateGenerator?=null
):ChannelInboundHandlerAdapter() {
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
        if(mCertificateUtil==null){
            clientCtx.channel().close()
            return
        }
        clientCtx.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))) //响应客户端
        val host=request.headers()["host"].let {
            if(it.contains(":"))
                it.substring(0,it.indexOf(':'))
            else
                it
        }
        clientCtx.pipeline().addFirst(mCertificateUtil.generateSslContext(host).newHandler(clientCtx.alloc()))//用于加解密https流量，将https请求转换为http请求
    }

    private fun handleHttpRequest(clientCtx: ChannelHandlerContext, request: FullHttpRequest){//Serve as a http server
        if(clientCtx.pipeline().last() !is HttpResponseEncoder)
            clientCtx.pipeline().addLast(HttpResponseEncoder())//直接输出响应
        val url=try {
            URL(request.uri())
        }catch (ex:MalformedURLException){
            val path=request.uri()
            val host=request.headers()["host"]
            URL("https://$host$path")
        }
        mOkHttpClientBuilder.build().newCall(Request.Builder().apply {
            url(url)
            request.headers().forEach {
                header(it.key,it.value)
            }
            val requestBody:RequestBody?
            if(request.method() == HttpMethod.POST)
            {
                val toRead=request.content().readableBytes()
                val byteArray=ByteArray(toRead)
                request.content().readBytes(byteArray)
                requestBody=byteArray.toRequestBody()
            }else
                requestBody=null
            method(request.method().name(),requestBody)
            header("accept-encoding","identity")
        }.build()).enqueue(object:Callback{
            override fun onFailure(call: Call, e: IOException) {
                clientCtx.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 500 Internal Server Error\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)))
                clientCtx.channel().close()
                mLogger.debug("连接${url}失败")
            }
            override fun onResponse(call: Call, response: Response) {
                val httpResponse=DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.valueOf(response.code))
                httpResponse.headers().also {
                    response.headers.forEach{header->
                        it[header.first]=header.second
                    }
                }
                response.body.use {body->
                    if(body!=null)
                        httpResponse.content().writeBytes(body.bytes())
                }
                clientCtx.pipeline().writeAndFlush(httpResponse)
                response.headers["connection"].also {
                    if(it!=null&&it.lowercase()=="close"){
                        clientCtx.channel().close()
                    }
                }
            }
        })
    }

}