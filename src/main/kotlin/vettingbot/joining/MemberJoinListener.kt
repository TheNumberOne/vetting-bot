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

package vettingbot.joining

import discord4j.core.event.domain.guild.MemberJoinEvent
import org.springframework.stereotype.Component
import vettingbot.discord.DiscordEventListener
import vettingbot.util.sendMessage

@Component
class MemberJoinListener(private val channelCreator: VettingChannelService) : DiscordEventListener<MemberJoinEvent> {
    override suspend fun on(event: MemberJoinEvent) {
        val channel = channelCreator.getOrCreateChannelFor(event.member)

        channel.sendMessage {
            content(event.member.mention)
            embed {
                description("Welcome to the server ${event.member.mention}! Please wait for verification before you access the rest of the server.")
            }
        }
    }
}