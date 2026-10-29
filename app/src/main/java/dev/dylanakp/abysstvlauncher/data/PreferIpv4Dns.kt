package dev.dylanakp.abysstvlauncher.data

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

object PreferIpv4Dns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        val v4 = addresses.filterIsInstance<Inet4Address>()
        val v6 = addresses.filter { it !is Inet4Address }
        return if (v4.isNotEmpty()) v4 else addresses
    }
}
