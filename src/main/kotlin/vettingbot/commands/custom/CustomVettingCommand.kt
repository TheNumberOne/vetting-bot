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
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import reactor.core.publisher.Mono
import vettingbot.command.AbstractCommand
import vettingbot.guild.GuildConfigService
import vettingbot.logging.GuildLoggerService
import vettingbot.util.*
import vettingbot.vetting.VettingChannelService
import java.time.Instant

class CustomVettingCommand(
    private val guildConfigService: GuildConfigService,
    private val config: CustomVettingCommandConfig,
    private val vettingChannelService: VettingChannelService,
    private val guildLoggerService: GuildLoggerService
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
        val mod = message.member.nullable!!

        val reason = if (args.isEmpty()) null else args
        if (config.ban) {
            val avatar = member.avatarUrl
            val tag = member.tag
            val banReason = reason ?: config.banReason
            member.ban {
                it.reason = banReason
            }.awaitCompletion()
            vettingChannelService.deleteVettingChannel(channel as GuildMessageChannel)
            guildLoggerService.getLogger(guild)?.sendEmbed {
                author("Ban $tag", iconUrl = avatar)
                description("Banned user while vetting.")
                field("User", tag, true)
                field("Mod", mod.mention, true)
                if (!banReason.isNullOrBlank()) {
                    field("Reason", banReason)
                }
                footer("ID: ${member.id.asString()}")
                timestamp(Instant.now())
            }
            return
        } else if (config.kick) {
            val avatar = member.avatarUrl
            val tag = member.tag
            val kickReason = reason ?: config.kickReason
            member.kick(kickReason).awaitCompletion()
            vettingChannelService.deleteVettingChannel(channel as GuildMessageChannel)
            guildLoggerService.getLogger(guild)?.sendEmbed {
                author("Kick $tag", iconUrl = avatar)
                description("Kicked user while vetting.")
                field("User", tag, true)
                field("Mod", mod.mention, true)
                if (!kickReason.isNullOrBlank()) {
                    field("Reason", kickReason)
                }
                footer("ID: ${member.id.asString()}")
                timestamp(Instant.now())
            }
            return
        }

        member.edit {
            it.setRoles(member.roleIds - config.removeRoles + config.addRoles)
        }.awaitCompletion()
        if (guildConfigService.getVettedRole(guild.id) in config.addRoles) {
            vettingChannelService.deleteVettingChannel(channel as GuildMessageChannel)
        }

        val pingChannel = config.pingChannel
        val pingMessage = config.pingMessage
        if (pingChannel != null && pingMessage != null) {
            val actualPingChannel =
                guild.getChannelById(pingChannel).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull() as? TextChannel
            actualPingChannel?.sendMessage {
                content(pingTemplate.expand(pingMessage) {
                    this.mod = mod.mention
                    this.member = member.mention
                    this.channel = actualPingChannel.mention
                })
            }
        }
    }
}