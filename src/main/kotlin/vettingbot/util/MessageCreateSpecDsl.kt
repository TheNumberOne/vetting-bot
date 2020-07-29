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
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.discordjson.json.AllowedMentionsData
import discord4j.discordjson.json.EmbedData
import discord4j.rest.util.AllowedMentions
import discord4j.rest.util.Color
import discord4j.rest.util.MultipartRequest
import kotlinx.coroutines.reactive.awaitSingle
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

inline fun messageDsl(dsl: MessageCreateSpecDsl.() -> Unit): MessageCreateTemplate {
    return MessageCreateSpecDsl(MessageCreateSpec()).apply(dsl).toTemplate()
}

inline fun embedDsl(dsl: EmbedCreateSpecDsl.() -> Unit): EmbedCreateTemplate {
    return EmbedCreateSpecDsl(EmbedCreateSpec()).apply(dsl).toTemplate()
}

suspend inline fun MessageCreateEvent.respondMessage(dsl: MessageCreateSpecDsl.() -> Unit): Message {
    return message.channel.awaitSingle().createMessage(messageDsl(dsl)).awaitSingle()
}

suspend inline fun MessageCreateEvent.respondEmbed(dsl: EmbedCreateSpecDsl.() -> Unit): Message {
    return message.channel.awaitSingle().createEmbed(embedDsl(dsl)).awaitSingle()
}

private fun MessageCreateSpec.fromData(request: MultipartRequest) {
    request.createRequest?.content()?.nullable?.let(this::setContent)
    request.createRequest?.tts()?.nullable?.let(this::setTts)
    request.createRequest?.embed()?.nullable?.let { embed -> setEmbed { it.fromData(embed) } }
    request.files.forEach { file -> addFile(file.t1, file.t2) }
    request.createRequest?.allowedMentions()?.nullable?.let { data -> setAllowedMentions(fromData(data)) }
}

class MessageCreateTemplate(private val data: MultipartRequest) : Consumer<MessageCreateSpec>, (MessageCreateSpec) -> Unit {

    override fun accept(spec: MessageCreateSpec) = spec.fromData(data)
    inline fun andThen(spec: MessageCreateSpecDsl.() -> Unit): MessageCreateTemplate {
        return messageDsl {
            accept(this.spec)
            spec()
        }
    }

    override fun invoke(spec: MessageCreateSpec) = spec.fromData(data)
}

class MessageCreateSpecDsl(val spec: MessageCreateSpec) {
    fun content(content: String) {
        spec.setContent(content)
    }

    fun nonce(nonce: Snowflake) {
        spec.setNonce(nonce)
    }

    fun tts(tts: Boolean) {
        spec.setTts(tts)
    }

    inline fun embed(dsl: EmbedCreateSpecDsl.() -> Unit) {
        spec.setEmbed(embedDsl(dsl))
    }

    fun file(fileName: String, file: InputStream) {
        spec.addFile(fileName, file)
    }

    fun fileSpoiler(fileName: String, file: InputStream) {
        spec.addFileSpoiler(fileName, file)
    }

    inline fun allowedMentions(dsl: AllowedMentionsBuilderDsl.() -> Unit) {
        spec.setAllowedMentions(AllowedMentionsBuilderDsl(AllowedMentions.builder()).apply(dsl).build())
    }

    fun toTemplate() = MessageCreateTemplate(spec.asRequest())
}

private fun EmbedCreateSpec.fromData(request: EmbedData) {
    request.title().nullable?.let(this::setTitle)
    request.description().nullable?.let(this::setDescription)
    request.url().nullable?.let(this::setUrl)
    request.timestamp().nullable?.let { setTimestamp(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(it))) }
    request.color().nullable?.let { setColor(Color.of(it)) }
    request.footer().nullable?.let { setFooter(it.text(), it.iconUrl().nullable) }
    request.image().nullable?.url()?.nullable?.let(this::setImage)
    request.thumbnail().nullable?.url()?.nullable?.let(this::setThumbnail)
    request.author().nullable?.let { author ->
        author.name().nullable?.let { name ->
            setAuthor(name, author.url().nullable, author.iconUrl().nullable)
        }
    }
    request.fields().nullable?.forEach { field ->
        field.inline().nullable?.let { inline ->
            addField(field.name(), field.value(), inline)
        }
    }
}

class EmbedCreateTemplate(private val data: EmbedData) : Consumer<EmbedCreateSpec>, (EmbedCreateSpec) -> Unit {
    override fun accept(spec: EmbedCreateSpec) = spec.fromData(data)
    inline fun andThen(spec: EmbedCreateSpecDsl.() -> Unit): EmbedCreateTemplate {
        return embedDsl {
            accept(this.spec)
            spec()
        }
    }

    override fun invoke(spec: EmbedCreateSpec) = spec.fromData(data)
}

class EmbedCreateSpecDsl(val spec: EmbedCreateSpec) {
    fun title(value: String) {
        spec.setTitle(value)
    }

    fun description(value: String) {
        spec.setDescription(value)
    }

    fun url(value: String) {
        spec.setUrl(value)
    }

    fun timestamp(timestamp: Instant) {
        spec.setTimestamp(timestamp)
    }

    fun color(color: Color) {
        spec.setColor(color)
    }

    fun footer(text: String, icon: String) {
        spec.setFooter(text, icon)
    }

    fun image(url: String) {
        spec.setImage(url)
    }

    fun thumbnail(url: String) {
        spec.setThumbnail(url)
    }

    fun author(name: String, url: String? = null, iconUrl: String? = null) {
        spec.setAuthor(name, url, iconUrl)
    }

    fun field(key: String, value: String, inline: Boolean = false) {
        spec.addField(key, value, inline)
    }

    fun toTemplate(): EmbedCreateTemplate = EmbedCreateTemplate(spec.asRequest())
}


private fun fromData(data: AllowedMentionsData): AllowedMentions {
    val builder = AllowedMentions.builder()
    data.parse().nullable?.map { parseType ->
        when (parseType) {
            "roles" -> AllowedMentions.Type.ROLE
            "users" -> AllowedMentions.Type.USER
            "everyone" -> AllowedMentions.Type.EVERYONE_AND_HERE
            else -> error("Unexpected parse type")
        }
    }?.toTypedArray()?.let(builder::parseType)
    data.roles().nullable?.map(Snowflake::of)?.toTypedArray()?.let(builder::allowRole)
    data.users().nullable?.map(Snowflake::of)?.toTypedArray()?.let(builder::allowUser)

    return builder.build()
}

class AllowedMentionsBuilderDsl(private val builder: AllowedMentions.Builder) {
    fun build(): AllowedMentions = builder.build()

    fun user(id: Snowflake) {
        builder.allowUser(id)
    }

    fun parseType(type: AllowedMentions.Type) {
        builder.parseType(type)
    }

    fun role(id: Snowflake) {
        builder.allowRole(id)
    }
}