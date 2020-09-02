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

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import kotlinx.coroutines.reactive.awaitFirst
import java.time.Duration

suspend fun Message.promptBoolean(userId: Snowflake, timeout: Duration = Duration.ofMinutes(5)): Boolean {
    addReaction(ReactionEmoji.unicode("✅")).awaitCompletion()
    addReaction(ReactionEmoji.unicode("\uD83D\uDEAB")).awaitCompletion()
    return client.on(ReactionAddEvent::class.java)
        .filter {
            it.messageId == id && it.emoji.asUnicodeEmoji().nullable?.raw in setOf(
                "✅",
                "\uD83D\uDEAB"
            ) && it.userId == userId
        }
        .map {
            it.emoji.asUnicodeEmoji().nullable?.raw == "✅"
        }
        .timeout(timeout)
        .awaitFirst()
}