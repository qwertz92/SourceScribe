package app.sourcescribe.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * Performs one request against an allow-listed provider origin.
 *
 * Chargeable transport, response-read, response-size, and spool failures are
 * reported as [ProviderErrorCode.SUBMISSION_UNCERTAIN]: the provider may have
 * accepted the request and no safely replayable response is available. For
 * non-chargeable calls those failures stay typed as [ProviderErrorCode.NETWORK],
 * [ProviderErrorCode.INVALID_RESPONSE], or [ProviderErrorCode.RESPONSE_STORAGE].
 * Cancellation is propagated unchanged and cancels the underlying call; this
 * boundary never retries a request or converts cancellation into a provider error.
 */
class ProviderHttp(client: OkHttpClient = OkHttpClient()) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS).readTimeout(480, TimeUnit.SECONDS)
        .callTimeout(480, TimeUnit.SECONDS).build()

    suspend fun perform(request: Request, mayCharge: Boolean, spool: ResponseSpool? = null): ByteArray {
        if (!request.url.isHttps || request.url.port != 443 || request.url.host !in ALLOWED_HOSTS) {
            throw ProviderError(ProviderErrorCode.INVALID_INPUT)
        }
        // Counted only when the caller asked to be told. The body is the same body either way: the wrapper
        // forwards every write unchanged and adds nothing to what is sent.
        val progress = currentCoroutineContext()[UploadProgress]
        val body = request.body
        val counted = if (progress == null || body == null) request
        else request.newBuilder().method(request.method, CountingBody(body, progress)).build()
        val call = client.newCall(counted)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ProviderError(
                        if (mayCharge) ProviderErrorCode.SUBMISSION_UNCERTAIN else ProviderErrorCode.NETWORK,
                    ))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw statusError(it, mayCharge)
                            val body = it.body
                            if (body.contentLength() > MAX_RESPONSE_BYTES) {
                                throw responseSizeError(mayCharge)
                            }
                            body.byteStream().use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (continuation.isActive) {
                                    val size = input.read(buffer)
                                    if (size < 0) break
                                    if (output.size() > MAX_RESPONSE_BYTES - size) {
                                        throw responseSizeError(mayCharge)
                                    }
                                    output.write(buffer, 0, size)
                                }
                                output.toByteArray()
                            }
                        }
                        if (continuation.isActive) {
                            try {
                                spool?.save(bytes)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                throw spoolError(mayCharge)
                            }
                            if (continuation.isActive) continuation.resume(bytes)
                        }
                    } catch (failure: ProviderError) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    } catch (cancelled: CancellationException) {
                        if (continuation.isActive) continuation.resumeWithException(cancelled)
                    } catch (_: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(ProviderError(
                            if (mayCharge) ProviderErrorCode.SUBMISSION_UNCERTAIN else ProviderErrorCode.NETWORK,
                        ))
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(ProviderError(
                            if (mayCharge) ProviderErrorCode.SUBMISSION_UNCERTAIN else ProviderErrorCode.NETWORK,
                        ))
                    }
                }
            })
        }
    }

    private fun responseSizeError(mayCharge: Boolean): ProviderError = ProviderError(
        if (mayCharge) ProviderErrorCode.SUBMISSION_UNCERTAIN else ProviderErrorCode.INVALID_RESPONSE,
    )

    private fun spoolError(mayCharge: Boolean): ProviderError = ProviderError(
        if (mayCharge) ProviderErrorCode.SUBMISSION_UNCERTAIN else ProviderErrorCode.RESPONSE_STORAGE,
    )

    private fun statusError(response: Response, mayCharge: Boolean): ProviderError {
        val code = when (response.code) {
            401 -> ProviderErrorCode.AUTHENTICATION
            // A 403 can also mean an account policy, inaccessible input or provider-wide limit.
            403 -> ProviderErrorCode.ACCESS_DENIED
            402 -> ProviderErrorCode.QUOTA
            429 -> ProviderErrorCode.RATE_LIMIT
            408 -> if (mayCharge) ProviderErrorCode.SUBMISSION_UNCERTAIN else ProviderErrorCode.NETWORK
            in 500..599 -> if (mayCharge) ProviderErrorCode.SUBMISSION_UNCERTAIN else ProviderErrorCode.SERVER
            else -> ProviderErrorCode.INVALID_INPUT
        }
        return ProviderError(code, retryAfter(response.header("Retry-After")), response.code)
    }

    /** Forwards the body byte for byte and says how much of it has reached the socket. */
    private class CountingBody(private val delegate: RequestBody, private val progress: UploadProgress) : RequestBody() {
        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        override fun isOneShot() = delegate.isOneShot()

        override fun isDuplex() = delegate.isDuplex()

        override fun writeTo(sink: BufferedSink) {
            val total = delegate.contentLength()
            var written = 0L
            val counting = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    written += byteCount
                    progress.onBytes(written, total)
                }
            }.buffer()
            delegate.writeTo(counting)
            counting.flush()
        }
    }

    companion object {
        private val ALLOWED_HOSTS = setOf("api.groq.com", "api.openai.com", "api.assemblyai.com", "api.eu.assemblyai.com")
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024

        fun retryAfter(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
            if (value == null) return null
            value.trim().toLongOrNull()?.let { return it.coerceIn(1, 86400) }
            return try {
                ((ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis + 999) / 1000).coerceIn(1, 86400)
            } catch (_: Exception) { null }
        }
    }
}

/**
 * How much of the current request's body has been written, for a caller that shows upload progress.
 *
 * It travels in the coroutine context rather than in [ProviderHttp.perform]'s parameters because the request
 * is built by the provider adapter: every adapter would otherwise have to carry a progress argument through a
 * concern none of them has. [onBytes] is called from the thread OkHttp writes the body on, once per written
 * block, so it has to be cheap and safe to call from another thread; a caller that writes anywhere expensive
 * hands the number on rather than doing the work there. [total] is -1 for a body of unknown length.
 */
class UploadProgress(val onBytes: (written: Long, total: Long) -> Unit) :
    AbstractCoroutineContextElement(UploadProgress) {
    companion object Key : CoroutineContext.Key<UploadProgress>
}
