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

package vettingbot.banwatch

import discord4j.core.`object`.audit.ActionType
import discord4j.core.event.domain.guild.MemberLeaveEvent
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import vettingbot.discord.DiscordEventListener
import vettingbot.util.nullable
import java.time.Duration
import java.time.Instant

private val maxLag = Duration.ofMinutes(1)

@Component
class BanWatchDiscordListener(private val banWatchService: BanWatchService) : DiscordEventListener<MemberLeaveEvent> {
    override suspend fun on(event: MemberLeaveEvent) {
        if (!banWatchService.isEnabled(event.guildId)) return
        val now = Instant.now()
        val guild = event.guild.awaitSingle()
        val bans = guild.getAuditLog {
            it.setActionType(ActionType.MEMBER_BAN_ADD)
        }.filter {
            it.targetId.nullable == event.user.id && Duration.between(it.id.timestamp, now) < maxLag
        }.collectList().awaitSingle()
        if (bans.isNotEmpty()) {
            val (ban) = bans
            banWatchService.onBan(guild.getMemberById(ban.responsibleUserId).awaitSingle(), now)
            return
        }
        val kicks = guild.getAuditLog {
            it.setActionType(ActionType.MEMBER_KICK)
        }.filter {
            it.targetId.nullable == event.user.id && Duration.between(it.id.timestamp, now) < maxLag
        }.collectList().awaitSingle()
        if (kicks.isNotEmpty()) {
            val (kick) = kicks
            banWatchService.onBan(guild.getMemberById(kick.responsibleUserId).awaitSingle(), now)
            return
        }
    }
}