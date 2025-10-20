package shared.utils

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.floor

object DateUtils {

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var gy2 = gy - 1600
        var days = 365 * gy2 + floor((gy2 + 3)/4.0).toInt() - floor((gy2 + 99)/100.0).toInt() + floor((gy2 + 399)/400.0).toInt()
        days += gdm[gm - 1]
        days += gd - 1
        if (gm > 2 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) days += 1

        var jy = 979
        var j_day_no = days - 79
        val j_np = j_day_no / 12053
        j_day_no %= 12053
        jy += 33 * j_np + 4 * (j_day_no / 1461)
        j_day_no %= 1461
        if (j_day_no >= 366) {
            jy += (j_day_no - 366) / 365
            j_day_no = (j_day_no - 366) % 365
        }
        val jm = if (j_day_no < 186) j_day_no / 31 + 1 else (j_day_no - 186) / 30 + 7
        val jd = if (j_day_no < 186) j_day_no % 31 + 1 else (j_day_no - 186) % 30 + 1
        return Triple(jy, jm, jd)
    }

    /** زمان جاری شمسی با فرمت YYYY/MM/DD HH:mm:ss */
    fun nowJalaliDateTime(): String {
        val c = GregorianCalendar.getInstance(Locale.US) as GregorianCalendar
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        val h = c.get(Calendar.HOUR_OF_DAY)
        val min = c.get(Calendar.MINUTE)
        val s = c.get(Calendar.SECOND)
        val (jy, jm, jd) = gregorianToJalali(y, m, d)
        return String.format(Locale.US, "%04d/%02d/%02d %02d:%02d:%02d", jy, jm, jd, h, min, s)
    }

    /** فرمت‌کردن زمان میلادی Long به شمسی */
    fun formatJalali(ms: Long): String {
        val c = GregorianCalendar.getInstance(Locale.US).apply { timeInMillis = ms }
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        val h = c.get(Calendar.HOUR_OF_DAY)
        val min = c.get(Calendar.MINUTE)
        val s = c.get(Calendar.SECOND)
        val (jy, jm, jd) = gregorianToJalali(y, m, d)
        return String.format(Locale.US, "%04d/%02d/%02d %02d:%02d:%02d", jy, jm, jd, h, min, s)
    }
}
