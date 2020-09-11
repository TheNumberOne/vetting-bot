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

package vettingbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import vettingbot.configuration.BotConfig
import vettingbot.configuration.OwnerConfig
import vettingbot.purge.PruneConfig
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

@EnableConfigurationProperties(BotConfig::class, OwnerConfig::class, PruneConfig::class)
@SpringBootApplication
class VettingBot

fun main(args: Array<String>) {
    val waitForHost = System.getenv("WAIT_FOR_HOST")
    if (waitForHost != null) {
        waitForHost(waitForHost)
    }
    runApplication<VettingBot>(*args)
}

private fun waitForHost(hostAndPort: String) {
    val (host, portStr) = hostAndPort.split(":")
    val port = portStr.toInt()
    val maxTimeout = System.getenv("WAIT_FOR_HOST_TIMEOUT_MS")?.toInt() ?: 60000
    val timeoutBetweenTries = System.getenv("WAIT_FOR_HOST_TIMEOUT_BETWEEN_TRIES")?.toInt() ?: 1000
    val start = Instant.now()
    val end = start + Duration.ofMillis(maxTimeout.toLong())
    var foundPort = false
    while (!foundPort && Instant.now() < end) {
        val timeLeft = Duration.between(Instant.now(), end)
        try {
            Socket().use {
                it.connect(InetSocketAddress(host, port), timeLeft.toMillis().toInt())
            }
        } catch (e: Exception) {
            val timeToPause =
                minOf(Duration.between(Instant.now(), end), Duration.ofMillis(timeoutBetweenTries.toLong()))
            Thread.sleep(timeToPause.toMillis())
            continue
        }
        foundPort = true
    }
    if (!foundPort) {
        println("Couldn't connect to $host within $maxTimeout ms.")
        exitProcess(1)
    }
}