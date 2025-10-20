package shared.utils

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.floor

object DateUtils {
    // تبدیل گرگوری به جلالی (خروجی: y, m, d)
    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var gy2 = gy - 1600
        var gm2 = gm
        var gd2 = gd
        var gDayNo = 365 * gy2 + floor((gy2 + 3)/4.0).toInt() - floor((gy2 + 99)/100.0).toInt() + floor((gy2 + 399)/400.0).toInt()
        gDayNo += gdm[gm2-1]
        if (gm2 > 2 && (gy2 % 4 == 0 && gy2 % 100 != 0 || gy2 % 400 == 0)) gDayNo += 1
        gDayNo += gd2 - 1

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 366) / 365
            jDayNo = (jDayNo - 366) % 365
        }

        val jm = if (jDayNo < 186) (jDayNo / 31) + 1 else ((jDayNo - 186) / 30) + 7
        val jd = if (jDayNo < 186) (jDayNo % 31) + 1 else ((jDayNo - 186) % 30) + 1
        return Triple(jy, jm, jd)
    }

    /** تاریخ/ساعت شمسی اکنون: "YYYY/MM/DD HH:mm:ss" */
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

    /** فرمت شمسی از millis ورودی */
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
