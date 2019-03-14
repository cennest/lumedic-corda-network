package net.corda.server

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*


fun Date.toLocalDate() : LocalDateTime {
    val cal = GregorianCalendar()
    cal.time = this
    val zdt = cal.toZonedDateTime()
    return zdt.toLocalDateTime()
}

fun LocalDateTime.toDate() : Date {
    val zdt = ZonedDateTime.of(this, ZoneId.systemDefault())
    val cal = GregorianCalendar.from(zdt)
    return cal.time
}

fun randomInt(min: Int, max: Int) : Int {
    if(min > max || max - min+1 > Int.MAX_VALUE) throw IllegalArgumentException("Invalid Range")
    return Random().nextInt(max - min + 1) + min
}


