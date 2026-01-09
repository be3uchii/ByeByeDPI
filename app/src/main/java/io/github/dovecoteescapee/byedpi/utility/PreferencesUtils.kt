package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import android.content.SharedPreferences
import io.github.dovecoteescapee.byedpi.data.Mode

// Простая замена библиотеке androidx.preference
fun Context.getPreferences(): SharedPreferences =
    getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

fun SharedPreferences.getStringNotNull(key: String, defValue: String): String =
    getString(key, defValue) ?: defValue

fun SharedPreferences.mode(): Mode =
    Mode.fromString(getStringNotNull("byedpi_mode", "vpn"))

// Эти функции упрощены, так как сложные настройки удалены
fun SharedPreferences.checkIpAndPortInCmd(): Pair<String?, String?> = Pair(null, null)

fun SharedPreferences.getProxyIpAndPort(): Pair<String, String> {
    val ip = getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
    val port = getStringNotNull("byedpi_proxy_port", "1080")
    return Pair(ip, port)
}

// Заглушка, чтобы не ломать старый код
fun SharedPreferences.getSelectedApps(): List<String> = emptyList()
