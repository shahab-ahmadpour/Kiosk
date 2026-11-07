package com.showroom.kiosk

import com.showroom.kiosk.core.ConfigException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object HttpsFiles {
    fun getText(url: String, token: String, maxBytes: Int): String {
        val body = ByteArrayOutputStream()
        openStream(url, token).use { input ->
            copyCapped(input, body, maxBytes.toLong())
        }
        return body.toString(Charsets.UTF_8.name())
    }

    fun download(url: String, token: String, dest: File, maxBytes: Long) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        try {
            dest.outputStream().use { output ->
                openStream(url, token).use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            throw ConfigException("download is larger than $maxBytes bytes")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: Exception) {
            dest.delete()
            throw error
        }
    }

    private fun copyCapped(input: InputStream, output: ByteArrayOutputStream, maxBytes: Long) {
        val buffer = ByteArray(4096)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw ConfigException("response is larger than $maxBytes bytes")
            output.write(buffer, 0, read)
        }
    }

    private fun openStream(original: String, token: String): InputStream {
        var current = original
        val home = URL(original).host
        repeat(4) {
            val parsed = URL(current)
            if (parsed.protocol != "https") throw ConfigException("URL must be https")
            val connection = (parsed.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", "ShowroomKiosk")
                if (token.isNotBlank() && parsed.host.equals(home, ignoreCase = true)) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            connection.connect()
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw ConfigException("redirect without a location")
                connection.disconnect()
                current = URL(parsed, location).toString()
                return@repeat
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                throw ConfigException("HTTP $code")
            }
            return connection.inputStream
        }
        throw ConfigException("too many redirects")
    }
}
