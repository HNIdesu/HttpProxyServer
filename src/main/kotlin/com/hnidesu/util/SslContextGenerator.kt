package com.hnidesu.util

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

class SslContextGenerator(private val mRootCertificate: X509Certificate, private val mPrivateKey: PrivateKey) {
    fun generateSslContext(hostname:String):SslContext {
        val subject=X500NameBuilder().addRDN(BCStyle.CN,hostname).build()
        val issuer=Certificate.getInstance(mRootCertificate.encoded).subject
        val keyPair= KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.genKeyPair()
        val contentSigner=JcaContentSignerBuilder("SHA256WITHRSA").build(mPrivateKey)
        val cert= CertificateFactory.getInstance("X509").generateCertificate(ByteArrayInputStream(
            X509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.currentTimeMillis()),
                Date(),
                Date(System.currentTimeMillis()+24*3600*1000),//one day
                subject,
                SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
            ).addExtension(Extension.subjectAlternativeName,true,GeneralNames(arrayOf(GeneralName(GeneralName.dNSName,hostname))))
                .build(contentSigner).toASN1Structure().encoded)) as X509Certificate
        return SslContextBuilder.forServer(keyPair.private,cert,mRootCertificate).build()
    }

}