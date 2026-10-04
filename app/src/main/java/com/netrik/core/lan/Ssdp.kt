package com.netrik.core.lan

/** SSDP/UPnP: descoberta por multicast e leitura da descrição do dispositivo. Partes puras. */
object Ssdp {
    const val ADDRESS = "239.255.255.250"
    const val PORT = 1900

    val searchRequest: ByteArray = (
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: upnp:rootdevice\r\n" +
            "USER-AGENT: Android UPnP/1.1 Netrik/1.0\r\n\r\n"
        ).toByteArray(Charsets.US_ASCII)

    /** Cabeçalho LOCATION de uma resposta HTTP/1.1 200 do SSDP. */
    fun location(response: String): String? = response.lineSequence()
        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    data class Description(val friendlyName: String?, val manufacturer: String?, val modelName: String?)

    /** Extrai os campos do device raiz do XML de descrição (UPnP Device Architecture). */
    fun parseDescription(xml: String): Description {
        fun tag(name: String) = Regex("<$name>\\s*([^<]{1,200}?)\\s*</$name>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.isNotBlank() }
        return Description(tag("friendlyName"), tag("manufacturer"), tag("modelName"))
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
}
