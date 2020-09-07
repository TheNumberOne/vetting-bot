/*
 * Copyright (C) 2020  Rosetta Roberts <rosettafroberts@gmail.com>
 *
 * This file is part of VettingBot.
 *
 * VettingBot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VettingBot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VettingBot.  If not, see <https://www.gnu.org/licenses/>.
 */

package vettingbot.util

import mu.KotlinLogging
import java.time.Duration

private val logger = KotlinLogging.logger {}
private const val durationRegexString = """(?x) # turn on comments
    \s* # optional leading whitespace
    (?<num> # match a number
        (?:[+-]\s*)? # leading sign
        (?:\d+) # digits
    )
    \s* # optional whitespace
    (?<mod>(?:[wdhms]|ms|ns)) # modifiers
"""

private val durationRegex = Regex(durationRegexString)
private val matchesAll = Regex("(?:$durationRegexString)+\\s*")

fun parseDuration(s: String): Duration? {
    val result = parseDurationImpl(s)
    if (result == null) {
        logger.info { "Failed to parse $s" }
    }
    return result
}

fun parseDurationImpl(s: String): Duration? {
    if (!matchesAll.matches(s)) return null
    val parts = durationRegex.findAll(s)
    var duration = Duration.ZERO
    for (part in parts) {
        val num = part.groups["num"]?.value?.toLongOrNull() ?: return null
        val mod = part.groups["mod"]?.value ?: return null
        duration = when (mod) {
            "w" -> duration.plusDays(7 * num)
            "d" -> duration.plusDays(num)
            "h" -> duration.plusHours(num)
            "m" -> duration.plusMinutes(num)
            "s" -> duration.plusSeconds(num)
            "ms" -> duration.plusMillis(num)
            "ns" -> duration.plusNanos(num)
            else -> return null
        }
    }
    return duration
}

fun Duration.toAbbreviatedString(fineDetail: Boolean = false) = buildString {
    if (isNegative) {
        append('-')
    }
    val positive = abs()
    val showSmolParts = fineDetail || positive < Duration.ofSeconds(1)
    val showVerySmolParts = fineDetail || (positive < Duration.ofMillis(1))
    if (positive.toDaysPart() >= 7) {
        append(positive.toDaysPart() / 7).append('w')
    }
    if (positive.toDaysPart() % 7 != 0L) {
        append(positive.toDaysPart() % 7).append('d')
    }
    if (positive.toHoursPart() > 0) {
        append(positive.toHoursPart()).append('h')
    }
    if (positive.toMinutesPart() > 0) {
        append(positive.toMinutesPart()).append('m')
    }
    if (positive.toSecondsPart() > 0) {
        append(positive.toSecondsPart()).append('s')
    }
    if (showSmolParts && positive.toMillisPart() > 0) {
        append(positive.toMillisPart()).append("ms")
    }
    if (showVerySmolParts && positive.toNanosPart() % 1000000 > 0) {
        append(positive.toNanosPart() % 1000000).append("ns")
    }
    if (isZero) {
        append("0s")
    }
}


fun EmbedCreateSpecDsl.displayDurationHelp() {
    field("Syntax", "`(<number><modifier>)+`", false)
    field("Number", "Can be positive or negative. No decimal digits/fractional numbers.", false)
    field("Modifiers", "w, d, h, m, s, ms, ns", false)
    field("w", "weeks", true)
    field("d", "days", true)
    field("h", "hours", true)
    field("m", "minutes", true)
    field("s", "seconds", true)
    field("ms", "milliseconds", true)
    field("ns", "nanoseconds", true)
    field(
        "Example", """
        |`1w-1m` would be 1 minute less than a week.
        |`5h3m` would be 5 hours and one minute.
        |`1w1d` would be 1 week and a day.
    """.trimMargin(), false
    )
}