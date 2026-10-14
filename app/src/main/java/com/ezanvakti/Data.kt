package com.ezanvakti

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

object Cities {
    val ALL = listOf(
        "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Aksaray", "Amasya", "Ankara", "Antalya", "Ardahan", "Artvin",
        "Aydın", "Balıkesir", "Bartın", "Batman", "Bayburt", "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur",
        "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli", "Diyarbakır", "Düzce", "Edirne", "Elazığ", "Erzincan",
        "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane", "Hakkari", "Hatay", "Iğdır", "Isparta", "İstanbul",
        "İzmir", "Kahramanmaraş", "Karabük", "Karaman", "Kars", "Kastamonu", "Kayseri", "Kırıkkale", "Kırklareli", "Kırşehir",
        "Kilis", "Kocaeli", "Konya", "Kütahya", "Malatya", "Manisa", "Mardin", "Mersin", "Muğla", "Muş",
        "Nevşehir", "Niğde", "Ordu", "Osmaniye", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop", "Sivas",
        "Şanlıurfa", "Şırnak", "Tekirdağ", "Tokat", "Trabzon", "Tunceli", "Uşak", "Van", "Yalova", "Yozgat", "Zonguldak"
    )

    fun ascii(s: String): String = s
        .replace('ç', 'c').replace('Ç', 'C').replace('ğ', 'g').replace('Ğ', 'G')
        .replace('ı', 'i').replace('İ', 'I').replace('ö', 'o').replace('Ö', 'O')
        .replace('ş', 's').replace('Ş', 'S').replace('ü', 'u').replace('Ü', 'U')
}

object Prefs {
    val NAMES = listOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")

    private fun sp(c: Context) = c.getSharedPreferences("ezan", Context.MODE_PRIVATE)

    fun city(c: Context): String = sp(c).getString("city", "Ankara") ?: "Ankara"
    fun setCity(c: Context, v: String) = sp(c).edit().putString("city", v).apply()

    fun notify(c: Context, i: Int) = sp(c).getBoolean("n$i", i != 1)
    fun setNotify(c: Context, i: Int, v: Boolean) = sp(c).edit().putBoolean("n$i", v).apply()

    fun sound(c: Context, i: Int) = sp(c).getBoolean("s$i", i != 1)
    fun setSound(c: Context, i: Int, v: Boolean) = sp(c).edit().putBoolean("s$i", v).apply()

    fun persistent(c: Context) = sp(c).getBoolean("persist", true)
    fun setPersistent(c: Context, v: Boolean) = sp(c).edit().putBoolean("persist", v).apply()

    fun cache(c: Context, key: String): String? = sp(c).getString(key, null)
    fun putCache(c: Context, key: String, v: String) = sp(c).edit().putString(key, v).apply()
}

data class Next(val index: Int, val at: ZonedDateTime)

/** Vakitleri Aladhan API'sinden (method 13 = Diyanet İşleri Başkanlığı) aylık çeker ve önbelleğe alır. */
object Repo {
    val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    private val keys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    private fun key(c: Context, ym: YearMonth) = "m_${Cities.ascii(Prefs.city(c))}_$ym"

    fun load(c: Context, ym: YearMonth): List<List<LocalTime>>? {
        val raw = Prefs.cache(c, key(c, ym)) ?: return null
        return try {
            raw.lines().filter { it.isNotBlank() }.map { l -> l.split(",").map { LocalTime.parse(it) } }
        } catch (e: Exception) {
            null
        }
    }

    fun times(c: Context, date: LocalDate): List<LocalTime>? =
        load(c, YearMonth.from(date))?.getOrNull(date.dayOfMonth - 1)?.takeIf { it.size == 6 }

    fun fetch(c: Context, ym: YearMonth): Boolean {
        return try {
            val city = URLEncoder.encode(Cities.ascii(Prefs.city(c)), "UTF-8")
            val url = URL(
                "https://api.aladhan.com/v1/calendarByCity/${ym.year}/${ym.monthValue}" +
                    "?city=$city&country=Turkey&method=13&timezonestring=Europe/Istanbul"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val data = JSONObject(body).getJSONArray("data")
            val sb = StringBuilder()
            for (i in 0 until data.length()) {
                val t = data.getJSONObject(i).getJSONObject("timings")
                sb.append(keys.joinToString(",") { t.getString(it).trim().take(5) }).append('\n')
            }
            if (data.length() < 28) return false
            Prefs.putCache(c, key(c, ym), sb.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Bu ay (ve ay sonuna yakınsa gelecek ay) eksikse indirir. Yeni veri geldiyse true döner. */
    fun ensure(c: Context): Boolean {
        val today = LocalDate.now(zone)
        var changed = false
        val months = mutableListOf(YearMonth.from(today))
        if (today.dayOfMonth >= 20) months += YearMonth.from(today).plusMonths(1)
        for (ym in months) {
            if (load(c, ym) == null && fetch(c, ym)) changed = true
        }
        return changed
    }

    fun nextPrayer(c: Context, now: ZonedDateTime): Next? {
        val today = now.toLocalDate()
        val fallback = times(c, today)
        for (offset in 0..2) {
            val date = today.plusDays(offset.toLong())
            val t = times(c, date) ?: fallback ?: continue
            for (i in 0..5) {
                val at = date.atTime(t[i]).atZone(zone)
                if (at.isAfter(now)) return Next(i, at)
            }
        }
        return null
    }
}
