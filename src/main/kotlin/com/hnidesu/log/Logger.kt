package com.hnidesu.log

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Logger(val TAG:String){
    companion object{
        private var mDebug:Boolean=false
        fun setDebug(b:Boolean){
            mDebug=b
        }
        private val mDateTimeFormat= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        fun log(tag:String,msg: String){
            println("${LocalDateTime.now().format(mDateTimeFormat)} $tag/Log $msg")
        }
        fun error(tag:String,msg: String,t:Throwable){
            println("${LocalDateTime.now().format(mDateTimeFormat)} $tag/Error $msg ${t.localizedMessage}")
        }
        fun debug(tag: String,msg: String){
            if(mDebug)
                println("${LocalDateTime.now().format(mDateTimeFormat)} $tag/Debug $msg")
        }
    }
    fun log(msg: String){
        log(TAG,msg)
    }

    fun error(msg: String,t:Throwable){
        error(TAG,msg,t)
    }

    fun debug(msg: String){
        debug(TAG,msg)
    }
}