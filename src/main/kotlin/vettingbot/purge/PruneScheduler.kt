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

package vettingbot.purge

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.stereotype.Component
import vettingbot.discord.DiscordGatewaySubscriber
import vettingbot.guild.GuildConfigService
import vettingbot.util.awaitCompletion
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Component
class PruneScheduler(
    private val pruneService: PruneService,
    private val pruneConfig: PruneConfig,
    private val guildConfig: GuildConfigService
) : DiscordGatewaySubscriber {
    override fun subscribe(gateway: GatewayDiscordClient) {
        GlobalScope.launch {
            while (true) {
                try {
                    slowPurgeAllGuilds(gateway)
                } catch (e: Throwable) {
                    logger.error(e) { "Unexpected error." }
                }
            }
        }
    }

    suspend fun slowPurgeAllGuilds(gateway: GatewayDiscordClient) {
        val guilds = gateway.guilds.collectList().awaitSingle().shuffled()
        if (guilds.isEmpty()) {
            delay(pruneConfig.frequency.toMillis())
            return
        }
        val startTime = Instant.now()
        for ((i, guild) in guilds.withIndex()) {
            try {
                purgeGuild(guild)
                val expectedTime =
                    startTime + pruneConfig.frequency.multipliedBy(i.toLong()).dividedBy(guilds.size.toLong())
                delay(Duration.between(startTime, expectedTime).toMillis())
            } catch (e: Throwable) {
                logger.error(e) { "Unexpected error." }
            }
        }
    }

    suspend fun purgeGuild(guild: Guild) {
        val daysToPurge = pruneService.findSchedule(guild.id) ?: return
        val vettingRole = guildConfig.getVettingRole(guild.id)
        guild.prune { spec ->
            spec.setDays(daysToPurge)
            vettingRole?.let { spec.addRole(it) }
            spec.setComputePruneCount(false)
        }.awaitCompletion()
    }
}