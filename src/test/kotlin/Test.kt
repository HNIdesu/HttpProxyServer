import com.hnidesu.log.Logger
import com.hnidesu.net.proxy.ProxyServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

fun main() {
    val keyStore= KeyStore.getInstance("PKCS12").apply {
        ClassLoader.getSystemClassLoader().getResourceAsStream("certificate.pfx").use { `is`->
            load(`is`, charArrayOf())
        }
    }
    val privateKey:PrivateKey
    val certificate:X509Certificate
    keyStore.aliases().nextElement().also {alias->
        privateKey=keyStore.getKey(alias, charArrayOf()) as PrivateKey
        certificate=keyStore.getCertificate(alias) as X509Certificate
    }
    Logger.setDebug(true)
    val serverAddress=InetSocketAddress(InetAddress.getLocalHost(),1111)
    ProxyServer.Builder(serverAddress)
        .initSslContext(certificate,privateKey)
        .addInterceptor { chain ->
            println("find url ${chain.request().url}")
            chain.proceed(chain.request())
        }
        .setExternalProxy(Proxy(Proxy.Type.HTTP,InetSocketAddress(InetAddress.getLoopbackAddress(),7890)))
        .build()
        .start()
    println("Proxy server is listening at $serverAddress")
}
