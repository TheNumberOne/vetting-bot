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
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.reactive.awaitSingle
import vettingbot.command.AbstractCommand
import vettingbot.guild.GuildConfigService
import vettingbot.util.awaitCompletion
import vettingbot.util.respondEmbed
import vettingbot.vetting.VettingChannelService

class CustomVettingCommand(
    private val guildConfigService: GuildConfigService,
    private val config: CustomVettingCommandConfig,
    private val vettingChannelService: VettingChannelService
) :
    AbstractCommand(config.name, "A custom command used for vetting.") {

    override suspend fun canExecute(guildId: Snowflake, member: Member): Boolean {
        if (!super.canExecute(guildId, member)) return false
        if (member.id in config.forbiddenUsers) return false
        if (member.id in config.allowedUsers) return true
        if (member.roleIds.intersect(config.forbiddenRoles).isNotEmpty()) return false
        if (member.roleIds.intersect(config.allowedRoles).isNotEmpty()) return true
        if (guildId in config.allowedRoles) return true
        return false
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val channel = message.message.channel.awaitSingle()
        val guild = message.guild.awaitSingle()
        val user = vettingChannelService.getUserForVettingChannel(channel.id)
        if (user == null) {
            message.respondEmbed {
                description("Can only run custom commands inside of vetting channels.")
            }
            return
        }
        val member = guild.getMemberById(user).awaitSingle()

        val reason = if (args.isEmpty()) null else args
        if (config.ban) {
            member.ban {
                it.reason = reason ?: config.banReason
            }.awaitCompletion()
            vettingChannelService.deleteVettingChannel(channel as GuildMessageChannel)
            return
        } else if (config.kick) {
            member.kick(reason ?: config.banReason).awaitCompletion()
            vettingChannelService.deleteVettingChannel(channel as GuildMessageChannel)
            return
        }

        member.edit {
            it.setRoles(member.roleIds - config.removeRoles + config.addRoles)
        }.awaitCompletion()
        if (guildConfigService.getVettedRole(guild.id) in config.addRoles) {
            vettingChannelService.deleteVettingChannel(channel as GuildMessageChannel)
        }
    }
}