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
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.Category
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.kotlin.core.publisher.cast
import vettingbot.archival.ArchiveChannelService
import vettingbot.util.awaitCompletion
import vettingbot.util.onDiscordNotFound
import vettingbot.util.toSnowflake
import vettingbot.util.wrapExceptions

// the max limit is 50, but we're using 40 to be safe.
private const val MAX_CHANNELS_PER_CATEGORY = 40


@Component
class VettingChannelService(
    private val vettingChannelRepository: VettingChannelRepository,
    private val categoryDataRepository: CategoryDataRepository,
    private val transactionalOperator: TransactionalOperator,
    private val archiveChannelService: ArchiveChannelService
) {
    suspend fun getOrCreateChannelFor(member: Member): GuildMessageChannel {
        val channel = wrapExceptions { getVettingChannelFor(member.client, member.guildId, member.id) }
        if (channel != null) return channel
        return wrapExceptions { createAndSaveVettingChannel(member) }
    }

    suspend fun getVettingChannelFor(member: Member): GuildMessageChannel? =
        getVettingChannelFor(member.client, member.guildId, member.id)

    suspend fun getVettingChannelFor(
        client: GatewayDiscordClient,
        guildId: Snowflake,
        userId: Snowflake
    ): GuildMessageChannel? {
        return getVettingChannelDataFor(guildId, userId)
            ?.channelId
            ?.let { id ->
                wrapExceptions {
                    client.getChannelById(id.toSnowflake()).onDiscordNotFound {
                        mono {
                            deleteVettingChannel(id.toSnowflake())
                            null
                        }
                    }.cast<GuildMessageChannel>().awaitFirstOrNull()
                }
            }
    }

    private suspend fun getVettingChannelDataFor(guildId: Snowflake, userId: Snowflake): VettingChannel? {
        return transactionalOperator.executeAndAwait {
            wrapExceptions {
                vettingChannelRepository.findByGuildIdAndUserId(guildId, userId)
            }
        }
    }

    @Transactional
    private suspend fun getOrSaveVettingChannelDataFor(member: Member, channel: GuildMessageChannel): VettingChannel {
        return transactionalOperator.executeAndAwait {
            getVettingChannelDataFor(member.guildId, member.id) ?: wrapExceptions {
                val vettingChannel =
                    vettingChannelRepository.save(VettingChannel(channel.id, member.id, member.guildId)).awaitSingle()
                val categoryId = channel.categoryId.orElse(null)?.asLong() ?: return@wrapExceptions vettingChannel
                val category = categoryDataRepository.findById(categoryId).awaitFirstOrNull()
                    ?: return@wrapExceptions vettingChannel
                val withChannel = category.copy(vettingChannels = category.vettingChannels + vettingChannel)
                categoryDataRepository.save(withChannel).awaitCompletion()
                vettingChannel
            }
        }!!
    }

    suspend fun getUserForVettingChannel(channelId: Snowflake): Snowflake? {
        return vettingChannelRepository.findById(channelId.asLong()).awaitFirstOrNull()?.userId
    }

    suspend fun createAndSaveVettingChannel(member: Member): TextChannel {
        val guild = wrapExceptions { member.guild.awaitSingle() }
        val category = wrapExceptions { getOrCreateNonFullVettingCategory(guild) }

        val channelName = "vetting-${member.displayName}-${member.id.asString()}"
        val channel = wrapExceptions {
            guild.createTextChannel {
                it.apply {
                    setName(channelName)
                    setParentId(category.id)
                    setTopic("Vetting member ${member.displayName}")

                    setPermissionOverwrites(
                        setOf(
                            PermissionOverwrite.forMember(
                                member.id,
                                PermissionSet.of(
                                    Permission.READ_MESSAGE_HISTORY,
                                    Permission.ADD_REACTIONS,
                                    Permission.SEND_MESSAGES,
                                    Permission.ATTACH_FILES,
                                    Permission.VIEW_CHANNEL
                                ),
                                PermissionSet.none()
                            )
                        ) + category.permissionOverwrites
                    )
                }
            }.awaitSingle()
        }

        try {
            val result = wrapExceptions { getOrSaveVettingChannelDataFor(member, channel) }

            // Other code created a channel first.
            if (result.channelId != channel.id.asLong()) {
                wrapExceptions { channel.delete("Accidentally created additional channel").awaitCompletion() }
                return wrapExceptions {
                    guild.getChannelById(result.channelId.toSnowflake()).awaitSingle()
                } as TextChannel
            }

            return channel
        } catch (e: Exception) {
            wrapExceptions { channel.delete("Failed to remember channel is a vetting channel").awaitCompletion() }
            throw e
        }
    }

    suspend fun deleteVettingChannel(channelId: Snowflake) {
        transactionalOperator.executeAndAwait {
            wrapExceptions {
                vettingChannelRepository.deleteById(channelId.asLong()).awaitCompletion()
            }
        }
    }

    suspend fun deleteVettingChannel(channel: GuildMessageChannel) {
        val userId = getUserForVettingChannel(channel.id) ?: error("Not a vetting channel.")
        archiveChannelService.archive(userId, channel)
        channel.delete().awaitCompletion()
        deleteVettingChannel(channel.id)
    }

    private suspend fun getOrCreateNonFullVettingCategory(guild: Guild): Category {
        return getNonFullVettingCategoryData(guild)?.id?.toSnowflake()
            ?.let { guild.getChannelById(it).awaitSingle() as Category } ?: createVettingCategory(guild)
    }

    private suspend fun getNonFullVettingCategoryData(guild: Guild): CategoryData? {
        return transactionalOperator.executeAndAwait {
            wrapExceptions {
                categoryDataRepository.findNonFullCategoriesByGuildId(guild.id, MAX_CHANNELS_PER_CATEGORY).asFlow()
                    .firstOrNull()
            }
        }
    }

    private suspend fun createVettingCategory(guild: Guild): Category {
        val templateData = wrapExceptions { getVettingCategoriesData(guild).first() }
        val template = wrapExceptions { guild.getChannelById(templateData.id.toSnowflake()).awaitSingle() } as Category
        return createVettingCategory(guild, template.name, template.permissionOverwrites, template.rawPosition + 1)
    }

    suspend fun createVettingCategory(
        guild: Guild,
        name: String,
        permissionOverwrites: Set<PermissionOverwrite>? = null,
        position: Int? = null
    ): Category {
        val category = wrapExceptions {
            guild.createCategory {
                it.apply {
                    setName(name)
                    permissionOverwrites?.let(this::setPermissionOverwrites)
                    position?.let(this::setPosition)
                }
            }.awaitSingle()
        }

        try {
            saveVettingCategoryData(CategoryData(category.id, guild.id, emptySet()))
            return category
        } catch (e: Exception) {
            wrapExceptions { category.delete("Unable to store completion of category.").awaitCompletion() }
            throw e
        }
    }

    suspend fun saveVettingCategory(category: Category) = saveVettingCategoryData(CategoryData(category))

    private suspend fun saveVettingCategoryData(category: CategoryData): CategoryData {
        return transactionalOperator.executeAndAwait {
            wrapExceptions { categoryDataRepository.save(category).awaitSingle() }
        }!!
    }

    private suspend fun getVettingCategoriesData(guild: Guild): Flow<CategoryData> {
        return transactionalOperator.executeAndAwait {
            wrapExceptions { categoryDataRepository.findByGuildId(guild.id).asFlow() }
        }!!
    }

    suspend fun getVettingCategories(guild: Guild): Flow<Category> {
        val client = guild.client
        return getVettingCategoriesData(guild).mapNotNull { data ->
            wrapExceptions {
                client.getChannelById(data.id.toSnowflake()).cast<Category>().onDiscordNotFound {
                    mono {
                        removeVettingCategory(data.id.toSnowflake())
                        null
                    }
                }.awaitFirstOrNull()
            }
        }
    }

    suspend fun removeVettingCategory(categoryId: Snowflake) {
        transactionalOperator.executeAndAwait {
            wrapExceptions { categoryDataRepository.deleteById(categoryId.asLong()).awaitCompletion() }
        }
    }
}