package de.lostmekka.sekiro.save.saver

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Long.toTimeString(): String = LocalDateTime
    .ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss SSS'ms'"))
