package com.hnidesu.net.proxy.internal

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal object TrustAllTrustManager:X509TrustManager {
    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return arrayOf()
    }

}