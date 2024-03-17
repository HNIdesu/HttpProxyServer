import com.hnidesu.log.Logger
import com.hnidesu.net.proxy.ProxyServer
import java.io.FileInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

fun main() {
    val keyStore= KeyStore.getInstance("PKCS12").apply {
        load(FileInputStream("E:\\Documents\\Scripts\\生成https证书\\certificate.pfx"), charArrayOf())
    }
    val privateKey=keyStore.getKey("1", charArrayOf()) as PrivateKey
    val certificate=keyStore.getCertificate("1") as X509Certificate
    Logger.setDebug(true)
    ProxyServer.Builder(1111)
        .initSSLContext(certificate,privateKey)
        .addInterceptor { chain ->
            println("find url ${chain.request().url}")
            chain.proceed(chain.request())
        }
        .setExternalProxy(Proxy(Proxy.Type.HTTP,InetSocketAddress(InetAddress.getLoopbackAddress(),7890)))
        .build()
        .start()
}
