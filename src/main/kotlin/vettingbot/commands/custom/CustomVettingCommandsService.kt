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
import vettingbot.vetting.VettingChannelService

@Component
class CustomVettingCommandsService(
    private val guildConfigService: GuildConfigService,
    private val vettingChannelService: VettingChannelService,
    private val repo: CustomVettingCommandConfigRepository,
    private val trans: TransactionalOperator
) {
    suspend fun findCommand(guildId: Snowflake, commandName: String): Command? {
        return repo.findByGuildIdAndName(guildId, commandName)
            ?.let { CustomVettingCommand(guildConfigService, it, vettingChannelService) }
    }

    suspend fun findCommandConfigsInGuild(guildId: Snowflake): List<CustomVettingCommandConfig> {
        return trans.executeAndAwait {
            repo.findByGuildId(guildId)
        }!!.toList()
    }

    /**
     * Returns the command if it successfully created a new command.
     * Returns null if the command already exists.
     */
    suspend fun createNewOrSetExisting(
        config: CustomVettingCommandConfig
    ): CustomVettingCommandConfig = trans.executeAndAwait {
        repo.save(config).awaitSingle()
    }!!

    /**
     * Returns the command if it successfully updates a command.
     * Returns null if the command doesn't exist.
     */
    suspend fun updateCommand(
        guildId: Snowflake,
        commandName: String,
        update: (CustomVettingCommandConfig) -> CustomVettingCommandConfig
    ): CustomVettingCommandConfig? = trans.executeAndAwait {
        val item = repo.findByGuildIdAndName(guildId, commandName)
        if (item != null) {
            val newItem = update(item)
            repo.save(newItem).awaitSingle()
        } else {
            null
        }
    }

    suspend fun delete(guildId: Snowflake, commandName: String) = trans.executeAndAwait {
        repo.deleteByGuildIdAndName(guildId, commandName)
    }

}