package shared.utils

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.floor

object DateUtils {

    /** تبدیل تاریخ میلادی به شمسی (y, m, d → jy, jm, jd) */
    private fun gregorianToJalali(cy: Int, cm: Int, cd: Int): Triple<Int, Int, Int> {
        val gDayNo: Int
        val jDayNo: Int
        val jy: Int
        val jm: Int
        val jd: Int
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy = cy - 1600
        val gm = cm - 1
        val gd = cd - 1
        gDayNo = 365 * gy + floor((gy + 3) / 4.0).toInt() - floor((gy + 99) / 100.0).toInt() + floor((gy + 399) / 400.0).toInt()
        val gDay = gDayNo + gDaysInMonth[gm] + gd
        jDayNo = gDay - 79
        val jNp = jDayNo / 12053
        val jDayInCycle = jDayNo % 12053
        jy = 979 + 33 * jNp + 4 * (jDayInCycle / 1461)
        val rem = jDayInCycle % 1461
        val jD = if (rem >= 366) rem - 1 else rem
        jm = if (jD < 186) 1 + jD / 31 else 7 + (jD - 186) / 30
        jd = if (jD < 186) 1 + jD % 31 else 1 + (jD - 186) % 30
        return Triple(jy, jm, jd)
    }

    /** تاریخ و ساعت فعلی به فرمت شمسی */
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

    /** تبدیل timestamp به تاریخ شمسی */
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
