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

import discord4j.core.`object`.entity.channel.Category
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import reactor.kotlin.core.publisher.whenComplete
import vettingbot.command.AbstractCommand
import vettingbot.util.awaitCompletion
import vettingbot.util.respondEmbed
import vettingbot.vetting.VettingChannelService

@Component
class SetVettingCategoryCommand(private val vettingChannelService: VettingChannelService) : AbstractCommand(
    "category",
    "Retrieves or sets the name of the category that new vetting channels are created under.",
    Permission.ADMINISTRATOR
) {
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
            val oldCategories = categories.toList()
            oldCategories.map { category ->
                category.edit { it.setName(args) }
            }.whenComplete().awaitCompletion()

            if (oldCategories.isEmpty()) {
                val existingCategory = guild.channels.asFlow()
                    .filterIsInstance<Category>()
                    .firstOrNull {
                        it.name.equals(args, ignoreCase = true)
                    }
                if (existingCategory == null) {
                    val category = vettingChannelService.createVettingCategory(guild, args)

                    message.respondEmbed {
                        title("Categories")
                        description("Created category ${category.mention}.")
                    }
                } else {
                    vettingChannelService.saveVettingCategory(existingCategory)

                    message.respondEmbed {
                        title("Categories")
                        description("Marked category ${existingCategory.mention} as a vetting category.")
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
                message.respondEmbed {
                    title("Categories")
                    description("Renamed $categoriesDescription to $args.")
                }
            }
        }
    }
}