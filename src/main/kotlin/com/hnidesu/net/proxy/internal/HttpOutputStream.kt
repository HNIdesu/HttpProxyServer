package com.hnidesu.net.proxy.internal

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.LastHttpContent
import okio.BufferedSource
import java.io.Closeable

internal class HttpOutputStream(private val mChannel: Channel,private val mBufferSize:Int=65536) :Closeable{
    private val mInnerBuffer=ByteArray(mBufferSize)
    private var mPosition=0
    fun flush(){
        if(mPosition!=0){
            mChannel.writeAndFlush(DefaultHttpContent(Unpooled.wrappedBuffer(mInnerBuffer,0,mPosition)))
            Thread.sleep((mPosition/5000).toLong())
            mPosition=0
        }
    }
    fun write(bufferedSource: BufferedSource):Int{
        val remaining=mBufferSize-mPosition
        val  bytesRead=bufferedSource.read(mInnerBuffer,mPosition,remaining)
        mPosition+=bytesRead
        if(bytesRead==remaining)
            flush()
        return bytesRead
    }

    override fun close() {
        flush()
        mChannel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    }


}