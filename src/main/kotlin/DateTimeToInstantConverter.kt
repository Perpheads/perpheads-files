package com.perpheads.files

import org.jooq.Converter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class DateTimeToInstantConverter : Converter<LocalDateTime, Instant> {
    override fun from(databaseObject: LocalDateTime?): Instant? = databaseObject?.toInstant(ZoneOffset.UTC)
    override fun to(userObject: Instant?): LocalDateTime? = userObject?.atOffset(ZoneOffset.UTC)?.toLocalDateTime()
    override fun fromType(): Class<LocalDateTime> = LocalDateTime::class.java
    override fun toType(): Class<Instant> = Instant::class.java
}