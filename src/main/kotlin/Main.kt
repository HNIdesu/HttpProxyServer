import com.hnidesu.log.Logger
import com.hnidesu.net.proxy.ProxyServer
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

fun main() {
    val sslContext= SSLContext.getInstance("TLS").apply {
        val keyStore= KeyStore.getInstance("PKCS12").apply {
            load(FileInputStream("E:\\Documents\\Scripts\\生成https证书\\certificate.pfx"), charArrayOf())
        }
        val keyManagers= KeyManagerFactory.getInstance("SunX509").apply { init(keyStore, charArrayOf()) }.keyManagers
        init(keyManagers,null, SecureRandom())
    }
    Logger.setDebug(true)
    ProxyServer.Builder(1111)
        .setSSLContext(sslContext)
        .build()
        .start()
}
