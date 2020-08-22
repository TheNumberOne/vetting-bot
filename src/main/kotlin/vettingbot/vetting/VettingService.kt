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

import discord4j.core.`object`.entity.Member
import org.springframework.stereotype.Component
import vettingbot.archival.ArchiveChannelService
import vettingbot.guild.GuildConfigService
import vettingbot.util.awaitCompletion
import vettingbot.util.sendMessage

@Component
class VettingService(
    private val guildConfigService: GuildConfigService,
    private val channelCreator: VettingChannelService,
    private val archiveChannelService: ArchiveChannelService
) {
    suspend fun needsVetting(member: Member): Boolean {
        if (member.isBot) return false
        if (!guildConfigService.isEnabled(member.guildId)) return false
        if (member.roleIds.contains(guildConfigService.getVettedRole(member.guildId))) return false
        return true
    }

    suspend fun beginVetting(member: Member) {
        if (!needsVetting(member)) return

        val vettingRole = guildConfigService.getVettingRole(member.guildId)

        if (vettingRole != null && !member.roleIds.contains(vettingRole)) {
            member.addRole(vettingRole, "Began the vetting process.").awaitCompletion()
        }

        val existingChannel = channelCreator.getVettingChannelFor(member)
        if (existingChannel != null) {
            existingChannel.sendMessage {
                content(member.mention)
                embed {
                    description("You have already began the vetting process.")
                }
            }
            return
        }

        val channel = channelCreator.createAndSaveVettingChannel(member)

        archiveChannelService.restoreArchiveFor(member.id, channel)

        val template = guildConfigService.getVettingText(member.guildId)
        val text = template.replace("{member}", member.mention)

        channel.sendMessage {
            content(member.mention)
            embed {
                description(text)
            }
        }

        archiveChannelService.startArchiving(member.id, channel)
    }
}