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

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import org.springframework.stereotype.Component
import vettingbot.util.awaitCompletion
import vettingbot.util.sendEmbed

@Component
class DeleteChannelMessageService(private val repo: DeleteMessageRepository) {
    suspend fun addDeleteMessage(channel: TextChannel) {
        val message = channel.sendEmbed {
            description("React with ❌ below to delete this channel.")
        }
        message.addReaction(ReactionEmoji.unicode("❌")).awaitCompletion()
        repo.save(DeleteMessage(channel.guildId, channel.id, message.id.asLong(), "❌")).awaitCompletion()
    }
}