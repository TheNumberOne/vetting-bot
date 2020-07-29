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

import discord4j.common.util.Snowflake
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.Category
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import vettingbot.guild.GuildConfigService

@Component
class VettingChannelService(private val guildConfigService: GuildConfigService) {
    suspend fun getOrCreateChannelFor(member: Member): GuildMessageChannel {
        val channelName = "vetting-${member.displayName}-${member.id.asString()}"
        val guild = member.guild.awaitSingle()
        val category = getNonFullVettingCategory(guild)

        return category.channels.asFlow().filterIsInstance<GuildMessageChannel>()
                .filter { it.name.startsWith("vetting-") && it.name.endsWith("-" + member.id.asString()) }
                .firstOrNull()
                ?: guild.createTextChannel {
                    it.apply {
                        setName(channelName)
                        setParentId(category.id)
                        setTopic("Vetting member ${member.displayName}")

                        setPermissionOverwrites(setOf(
                                PermissionOverwrite.forMember(
                                        member.id,
                                        PermissionSet.of(Permission.READ_MESSAGE_HISTORY, Permission.ADD_REACTIONS, Permission.SEND_MESSAGES, Permission.ATTACH_FILES, Permission.VIEW_CHANNEL),
                                        PermissionSet.none()
                                )
                        ))
                    }
                }.awaitSingle()
    }

    suspend fun findChannelsFor(guild: Guild, userId: Snowflake): Flow<GuildMessageChannel> {
        return guild.channels.asFlow()
                .filterIsInstance<GuildMessageChannel>()
                .filter { it.name.startsWith("vetting-") }
                .filter { it.name.endsWith("-" + userId.asString()) }
    }

    @Transactional
    suspend fun getNonFullVettingCategory(guild: Guild): Category {
        val id = guildConfigService.getCategory(guild.id)
        if (id != null) {
            val channel = guild.getChannelById(id).awaitSingle() as Category
            if (channel.channels.count().awaitSingle() < 50) {
                return channel
            }
        }
        val name = guildConfigService.getCategoryName(guild.id)
        val matchingCategories = getVettingCategories(guild)

        val matchingCategory = matchingCategories.firstOrNull { it.channels.count().awaitSingle() < 50 }

        if (matchingCategory != null) {
            guildConfigService.setCategory(guild.id, matchingCategory.id)
            return matchingCategory
        }

        val lastCategory = matchingCategories
                .map { it.position.awaitSingle() }
                .fold(null as Int?) { left, right ->
                    if (left == null || left < right)
                        right
                    else
                        left
                }


        val permissions = matchingCategories.firstOrNull()?.permissionOverwrites

        val category = guild.createCategory {
            it.apply {
                setName(name)
                if (lastCategory != null) setPosition(lastCategory)
                if (permissions != null) setPermissionOverwrites(permissions)
            }
        }.awaitSingle()

        guildConfigService.setCategory(guild.id, category.id)

        return category
    }

    suspend fun getVettingCategories(guild: Guild): Flow<Category> {
        val name = guildConfigService.getCategoryName(guild.id)

        return guild.channels.asFlow()
                .filterIsInstance<Category>()
                .filter {
                    it.name.toLowerCase() == name.toLowerCase()
                }
    }
}