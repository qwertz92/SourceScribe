package app.sourcescribe.extractor

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Public connectivity probe; reports only DNS counts/status codes, never response bodies. */
@RunWith(AndroidJUnit4::class)
class NetworkProbeTest {
    @Test
    fun pythonDnsUnderAppProcess() = runBlocking {
        val runtime = NativeRuntime(InstrumentationRegistry.getInstrumentation().targetContext)
        val script = """
            import ctypes, os, socket
            print("uid", os.getuid())
            print("dns_mode", os.environ.get("ANDROID_DNS_MODE", "unset"))
            succeeded = False
            for family in (socket.AF_UNSPEC, socket.AF_INET, socket.AF_INET6):
                for flags in (0, socket.AI_ADDRCONFIG):
                    try:
                        rows = socket.getaddrinfo("www.youtube.com", 443, family, socket.SOCK_STREAM, 0, flags)
                        print("family", family, "flags", flags, "count", len(rows))
                        if family == socket.AF_UNSPEC and flags == 0: succeeded = bool(rows)
                    except OSError as e:
                        print("family", family, "flags", flags, "errno", e.errno)
            for symbol in ("android_getprocnetwork", "android_getprocdns"):
                try:
                    fn = getattr(ctypes.CDLL(None, use_errno=True), symbol)
                    value = ctypes.c_uint64()
                    print(symbol, fn(ctypes.byref(value)), value.value)
                except AttributeError:
                    print(symbol, "unavailable")
            raise SystemExit(0 if succeeded else 1)
        """.trimIndent()
        val result = runtime.pythonForTest(listOf("-c", script), 60)
        val status = Bundle()
        status.putInt("pythonDns.exit", result.exitCode)
        status.putBoolean("pythonDns.noAddress", result.stderr.contains("No address associated with hostname"))
        status.putBoolean("pythonDns.permission", result.stderr.contains("Permission denied"))
        status.putString("pythonDns.matrix", result.stdout)
        InstrumentationRegistry.getInstrumentation().sendStatus(103, status)
        assertTrue(result.exitCode == 0)
    }

    @Test
    fun publicDnsAndTls() = runBlocking {
        withContext(Dispatchers.IO) {
            val status = Bundle()
            for (host in listOf("www.youtube.com", "github.com")) {
                val addresses = InetAddress.getAllByName(host)
                assertTrue(addresses.isNotEmpty())
                status.putInt("dns.$host", addresses.size)
                val connection = URL("https://$host/").openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "HEAD"
                    val code = connection.responseCode
                    assertTrue(code in 200..499)
                    status.putInt("http.$host", code)
                } finally {
                    connection.disconnect()
                }
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(102, status)
        }
    }
}
