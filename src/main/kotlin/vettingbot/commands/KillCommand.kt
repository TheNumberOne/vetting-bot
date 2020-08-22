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
import discord4j.core.`object`.entity.Member
import discord4j.core.event.domain.message.MessageCreateEvent
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.configuration.OwnerConfig
import vettingbot.discord.GatewayRunner
import kotlin.system.exitProcess

@Component
class KillCommand(private val ownerConfig: OwnerConfig, @Lazy private val gatewayRunner: GatewayRunner) :
    AbstractCommand("kill", "Kills the bot.") {
    override suspend fun canExecute(guildId: Snowflake, member: Member): Boolean {
        return member.id.asLong() == ownerConfig.id
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        gatewayRunner.stop()
        exitProcess(0)
    }
}