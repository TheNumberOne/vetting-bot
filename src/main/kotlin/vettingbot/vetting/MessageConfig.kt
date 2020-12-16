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

package vettingbot.vetting

import discord4j.common.util.Snowflake
import discord4j.core.`object`.reaction.ReactionEmoji
import org.springframework.data.annotation.Id
import org.springframework.data.neo4j.core.schema.Node
import vettingbot.util.nullable

@Node
class MessageConfig(@Id val id: Long, val guildId: Snowflake, val channelId: Snowflake, val emojiId: Snowflake?, val emojiName: String?) {
    fun isVettingEmoji(emoji: ReactionEmoji): Boolean {
        return isVettingEmoji(emoji.asCustomEmoji().nullable?.id, emoji.asUnicodeEmoji().nullable?.raw)
    }

    fun isVettingEmoji(emojiId: Snowflake?, emojiName: String?): Boolean {
        if (this.emojiId != null || emojiId != null) {
            return this.emojiId == emojiId
        }
        return this.emojiName == emojiName
    }
}