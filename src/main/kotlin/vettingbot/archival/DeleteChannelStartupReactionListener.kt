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

package vettingbot.archival

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.guild.GuildCreateEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.kotlin.core.publisher.cast
import reactor.kotlin.core.publisher.whenComplete
import vettingbot.discord.DiscordEventListener
import vettingbot.util.awaitCompletion
import vettingbot.util.nullable
import vettingbot.util.onDiscordNotFound
import vettingbot.util.toSnowflake

@Component
class DeleteChannelStartupReactionListener(
    private val repo: DeleteMessageRepository,
    private val trans: TransactionalOperator
) : DiscordEventListener<GuildCreateEvent> {
    override suspend fun on(event: GuildCreateEvent) {
        val guild = event.guild
        val channels = mutableSetOf<Snowflake>()
        trans.executeAndAwait {
            repo.findByGuildId(guild.id).collect { messageInfo ->
                val message = event.client.getMessageById(messageInfo.channelId, messageInfo.id.toSnowflake())
                    .onDiscordNotFound {
                        repo.delete(messageInfo).then().cast()
                    }
                    .awaitFirstOrNull() ?: return@collect
                if (message.reactions.any { it.emoji.asUnicodeEmoji().nullable?.raw == messageInfo.emoji }) {
                    repo.delete(messageInfo).awaitCompletion()
                    channels.add(messageInfo.channelId)
                }
            }
        }
        channels.map { id ->
            guild.getChannelById(id).flatMap { it.delete() }
        }.whenComplete().awaitCompletion()
    }
}