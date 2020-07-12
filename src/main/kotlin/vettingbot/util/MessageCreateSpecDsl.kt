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
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.AllowedMentions
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle

class MessageCreateSpecDsl(private val spec: MessageCreateSpec) {
    fun embed(dsl: EmbedCreateSpecDsl.() -> Unit) {
        spec.setEmbed { embedSpec ->
            EmbedCreateSpecDsl(embedSpec).apply(dsl)
        }
    }
    fun allowedMentions(dsl: AllowedMentionsBuilderDsl.() -> Unit) {
        spec.setAllowedMentions(AllowedMentionsBuilderDsl(AllowedMentions.builder()).apply(dsl).build())
    }
    fun content(content: String) {
        spec.setContent(content)
    }
}

class EmbedCreateSpecDsl(private val spec: EmbedCreateSpec) {
    fun title(value: String) {
        spec.setTitle(value)
    }
    fun description(value: String) {
        spec.setDescription(value)
    }
}

class AllowedMentionsBuilderDsl(private val builder: AllowedMentions.Builder) {
    fun build(): AllowedMentions = builder.build()
    fun user(id: Snowflake) {
        builder.allowUser(id)
    }
    fun member(member: Member) = user(member.id)
}

fun message(dsl: MessageCreateSpecDsl.() -> Unit): (MessageCreateSpec) -> Unit = { spec ->
    MessageCreateSpecDsl(spec).apply(dsl)
}

suspend fun MessageCreateEvent.respond(dsl: MessageCreateSpecDsl.() -> Unit): Message {
    return message.channel.awaitSingle().createMessage(message(dsl)).awaitSingle()
}