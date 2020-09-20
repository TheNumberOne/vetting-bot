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

package vettingbot.logging

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.TextChannel
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.cast
import vettingbot.util.awaitCompletion
import vettingbot.util.onDiscordNotFound

@Component
class GuildLoggerService(private val guildLoggerRepository: GuildLoggerRepository) {
    suspend fun markLogger(channel: TextChannel) {
        guildLoggerRepository.save(GuildLogger(channel.guildId, channel.id)).awaitCompletion()
    }

    suspend fun getLogger(guild: Guild): TextChannel? {
        val channelId = guildLoggerRepository.findById(guild.id).awaitFirstOrNull()?.channelId ?: return null
        return guild.getChannelById(channelId).onDiscordNotFound { Mono.empty() }.cast<TextChannel>().awaitFirstOrNull()
    }
}