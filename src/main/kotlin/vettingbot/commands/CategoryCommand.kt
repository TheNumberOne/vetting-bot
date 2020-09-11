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

package vettingbot.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.Category
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.whenComplete
import vettingbot.command.AbstractCommand
import vettingbot.util.*
import vettingbot.vetting.VettingChannelService

@Component
class CategoryCommand(private val vettingChannelService: VettingChannelService) : AbstractCommand(
    listOf("category", "categories"),
    "Retrieves or sets the name of the category that new vetting channels are created under.",
    Permission.ADMINISTRATOR
) {
    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
        description("When users begin the vetting process, a channel specifically for them is created. This command allows you to create or rename the categories that these vetting channels are created under. It is possible for multiple categories to be created if there are a large number of vetting channels created.")
        field(
            "Syntax", """
            `category` - Lists the names of the categories currently being used.
            `category name` - Renames existing categories if they exist. Otherwise, new vetting channels are created under the category with the specific name. If such a category doesn't exist, then it is created.
        """.trimIndent()
        )
        field(
            "Example Usage", """
            `category Vetting` - Renames/uses existing/creates the category named `Vetting`. New vetting channels are created under this category.
        """.trimIndent()
        )
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guild = message.guild.awaitSingle()
        val categories = vettingChannelService.getVettingCategories(guild)
        if (args.isBlank()) {
            val mentions = categories.map { it.mention }.toList()
            message.respondEmbed {
                title("Categories")
                description(
                    when {
                        mentions.isEmpty() -> "There are currently no categories configured for vetting."
                        mentions.size == 1 -> "The category used for vetting is ${mentions.first()}."
                        else -> "The categories used for vetting are ${mentions.joinToString(", ")}"
                    }
                )
            }
        } else {
            createOrRenameCategories(guild, categories, args, message.message.channel.awaitSingle() as TextChannel)
        }
    }

    suspend fun createOrRenameCategories(
        guild: Guild,
        currentCategories: List<Category>,
        name: String,
        respondChannel: TextChannel
    ) {
        val oldCategories = currentCategories.toList()
        oldCategories.map { category ->
            category.edit { it.setName(name) }
        }.whenComplete().awaitCompletion()

        if (oldCategories.isEmpty()) {
            val ids = findAndParseAllSnowflakes(name)
            val existingCategories = ids.mapNotNull {
                guild.getChannelById(it).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull() as? Category
            }.ifEmpty {
                guild.channels.asFlow()
                    .filterIsInstance<Category>()
                    .filter {
                        it.name.equals(name, ignoreCase = true)
                    }
                    .toList()
            }

            if (existingCategories.isEmpty()) {
                val newCategory = vettingChannelService.createVettingCategory(guild, name)

                respondChannel.sendEmbed {
                    title("Categories")
                    description("Created category ${newCategory.mention}.")
                }
            } else {
                existingCategories.forEach {
                    vettingChannelService.saveVettingCategory(it)
                }

                respondChannel.sendEmbed {
                    title("Categories")
                    if (existingCategories.size == 1) {
                        description("Marked category ${existingCategories.single().name} as a vetting category.")
                    } else {
                        description(
                            "Marked ${existingCategories.size} categories named ${
                                existingCategories.map { it.name }.distinct().joinToString()
                            } as vetting categories."
                        )
                    }
                }
            }
        } else {
            val categoriesDescription = oldCategories.groupBy { it.name }
                .entries
                .map { it.key to it.value.count() }
                .joinToString(", ") { (name, numChannels) ->
                    if (numChannels == 1) {
                        "category $name"
                    } else {
                        "$numChannels categories named $name"
                    }
                }
            respondChannel.sendEmbed {
                title("Categories")
                description("Renamed $categoriesDescription to $name.")
            }
        }
    }
}