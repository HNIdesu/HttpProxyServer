package com.hnidesu.net.proxy.utility

import java.io.FileInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object CertificateUtility {
    private val mPrivateKey:PrivateKey
    private val mPublicKey:PublicKey
    fun stub(){

    }
    init {
        val keyStore= KeyStore.getInstance("PKCS12").apply {
            load(FileInputStream("E:\\Documents\\Scripts\\生成https证书\\certificate.pfx"), charArrayOf())
        }
        mPrivateKey=keyStore.getKey("1", charArrayOf()) as PrivateKey
        (keyStore.getCertificate("1") as X509Certificate).also {cert->
            mPublicKey=cert.publicKey
        }
    }
    fun generateCertificate():X509Certificate{
        CertificateFactory.getInstance("X.509")
        TODO("")
    }
}