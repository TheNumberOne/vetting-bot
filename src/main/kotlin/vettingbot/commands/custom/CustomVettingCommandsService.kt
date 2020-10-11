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

package vettingbot.commands.custom

import discord4j.common.util.Snowflake
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import vettingbot.command.Command
import vettingbot.guild.GuildConfigService
import vettingbot.logging.GuildLoggerService
import vettingbot.util.wrapExceptions
import vettingbot.vetting.VettingChannelService

@Component
class CustomVettingCommandsService(
    private val guildConfigService: GuildConfigService,
    private val vettingChannelService: VettingChannelService,
    private val repo: CustomVettingCommandConfigRepository,
    private val trans: TransactionalOperator,
    private val guildLoggerService: GuildLoggerService,
) {
    suspend fun findCommand(guildId: Snowflake, commandName: String): Command? {
        return wrapExceptions {
            repo.findByGuildIdAndName(guildId, commandName.toLowerCase())
        }?.let {
            CustomVettingCommand(guildConfigService, it, vettingChannelService, guildLoggerService)
        }
    }

    suspend fun findCommandConfigsInGuild(guildId: Snowflake): List<CustomVettingCommandConfig> {
        return wrapExceptions {
            trans.executeAndAwait {
                repo.findByGuildId(guildId)
            }!!
        }.toList()
    }

    /**
     * Returns the command if it successfully created a new command.
     * Returns null if the command already exists.
     */
    suspend fun createNewOrSetExisting(
        config: CustomVettingCommandConfig
    ): CustomVettingCommandConfig = wrapExceptions {
        trans.executeAndAwait {
            repo.save(config.copy(name = config.name.toLowerCase())).awaitSingle()
        }!!
    }

    data class UpdateCommandResult(val previous: CustomVettingCommandConfig, val new: CustomVettingCommandConfig)

    /**
     * Returns the command if it successfully updates a command.
     * Returns null if the command doesn't exist.
     */
    suspend fun updateCommand(
        guildId: Snowflake,
        commandName: String,
        update: (CustomVettingCommandConfig) -> CustomVettingCommandConfig
    ): UpdateCommandResult? = wrapExceptions {
        trans.executeAndAwait {
            val item = repo.findByGuildIdAndName(guildId, commandName.toLowerCase())
            if (item != null) {
                val newItem = update(item)
                val result = repo.save(newItem.copy(name = newItem.name.toLowerCase())).awaitSingle()
                UpdateCommandResult(item, result)
            } else {
                null
            }
        }
    }

    suspend fun delete(guildId: Snowflake, commandName: String) = wrapExceptions {
        trans.executeAndAwait {
            repo.deleteByGuildIdAndName(guildId, commandName.toLowerCase())
        }
    }

}