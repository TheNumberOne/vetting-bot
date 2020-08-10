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

import discord4j.core.event.domain.message.ReactionAddEvent
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import vettingbot.discord.DiscordEventListener
import vettingbot.util.awaitCompletion
import vettingbot.util.nullable

@Component
class MessageReactionListener(private val messageService: MessageService, private val vettingService: VettingService) :
    DiscordEventListener<ReactionAddEvent> {
    override suspend fun on(event: ReactionAddEvent) {
        val member = event.member.nullable ?: return

        val messageId = event.messageId
        val emoji = event.emoji
        val vettingConfig = messageService.getVettingMessage(messageId) ?: return

        event.message.awaitSingle()?.removeReaction(emoji, event.userId)?.awaitCompletion()

        if (vettingConfig.isVettingEmoji(emoji)) {
            vettingService.beginVetting(member)
        }
    }
}