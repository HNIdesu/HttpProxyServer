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
        ClassLoader.getSystemClassLoader().getResourceAsStream("certificate.pfx").use {`is`->
            load(`is`, charArrayOf())
        }
    }
    val privateKey=keyStore.getKey("1", charArrayOf()) as PrivateKey
    val certificate=keyStore.getCertificate("1") as X509Certificate
    Logger.setDebug(true)
    ProxyServer.Builder(InetAddress.getLocalHost(),1111)
        .initSSLContext(certificate,privateKey)
        .addInterceptor { chain ->
            println("find url ${chain.request().url}")
            chain.proceed(chain.request())
        }
        .setExternalProxy(Proxy(Proxy.Type.HTTP,InetSocketAddress(InetAddress.getLoopbackAddress(),7890)))
        .build()
        .start()
}
