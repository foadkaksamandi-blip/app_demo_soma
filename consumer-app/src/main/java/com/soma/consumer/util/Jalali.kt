package com.soma.consumer.util

import java.util.*

object Jalali {
    data class JDate(val y: Int, val m: Int, val d: Int)

    private fun gregorianToJdn(y: Int, m: Int, d: Int): Int {
        val a = (14 - m) / 12
        val y2 = y + 4800 - a
        val m2 = m + 12 * a - 3
        return d + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045
    }

    private fun jdnToJalali(jdn: Int): JDate {
        val depoch = jdn - gregorianToJdn(622, 3, 22)
        val cycle = depoch / 1029983
        val cyear = depoch % 1029983
        val ycycle = if (cyear == 1029982) 2820 else {
            val aux1 = cyear / 366
            val aux2 = cyear % 366
            (2134 * aux1 + 2816 * aux2 + 2815) / 1028522 + aux1 + 1
        }
        var jy = ycycle + 2820 * cycle + 1
        var yday = jdn - jalaliToJdn(jy, 1, 1) + 1
        var jm: Int
        var jd: Int
        if (yday <= 186) {
            jm = ((yday - 1) / 31) + 1
            jd = ((yday - 1) % 31) + 1
        } else {
            yday -= 186
            jm = ((yday - 1) / 30) + 7
            jd = ((yday - 1) % 30) + 1
        }
        return JDate(jy, jm, jd)
    }

    private fun jalaliToJdn(y: Int, m: Int, d: Int): Int {
        val epbase = y - if (y >= 0) 474 else 473
        val epyear = 474 + (epbase % 2820)
        return d + if (m <= 7) (m - 1) * 31 else (m - 1) * 30 - (m - 7) +
            ((epyear * 682 - 110) / 2816) + (epyear - 1) * 365 + (epbase / 2820) * 1029983 + (1948320 - 1)
    }

    fun now(): String {
        val cal = Calendar.getInstance()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val hh = cal.get(Calendar.HOUR_OF_DAY)
        val mm = cal.get(Calendar.MINUTE)
        val ss = cal.get(Calendar.SECOND)
        val j = jdnToJalali(gregorianToJdn(gy, gm, gd))
        return "%04d/%02d/%02d %02d:%02d:%02d".format(j.y, j.m, j.d, hh, mm, ss)
    }
}
