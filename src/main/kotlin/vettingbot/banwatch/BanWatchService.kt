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

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.util.OrderUtil
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.kotlin.core.publisher.whenComplete
import vettingbot.util.awaitCompletion
import vettingbot.util.sendEmbed
import java.time.Duration
import java.time.Instant

@Component
class BanWatchService(private val repository: BanWatchRepository, private val trans: TransactionalOperator) {
    suspend fun enableBanWatch(guildId: Snowflake, interval: Duration, maxBans: Int) {
        repository.save(BanWatch(guildId, interval, maxBans)).awaitCompletion()
    }

    suspend fun isEnabled(guildId: Snowflake): Boolean {
        return repository.findById(guildId).awaitFirstOrNull() != null
    }

    suspend fun find(guildId: Snowflake): BanWatch? {
        return repository.findById(guildId).awaitFirstOrNull()
    }

    suspend fun disable(guildId: Snowflake) {
        repository.deleteById(guildId).awaitCompletion()
    }

    suspend fun onBan(banner: Member, time: Instant) {
        if (banner.isBot) return
        val exceededLimits = trans.executeAndAwait {
            val banWatch = repository.findById(banner.guildId).awaitFirstOrNull() ?: return@executeAndAwait false
            val newEntries = (banWatch.entries + BanEntry(banner.id, time)).filter {
                Duration.between(it.time, time) < banWatch.interval
            }.toSet()
            repository.save(banWatch.copy(entries = newEntries)).awaitCompletion()
            newEntries.filter { it.memberId == banner.id }.size >= banWatch.maxBans
        }!!

        if (exceededLimits) {
            val roles = banner.roles.collectList().awaitSingle()
            val toRemove = roles.filter {
                (setOf(
                    Permission.ADMINISTRATOR,
                    Permission.BAN_MEMBERS,
                    Permission.KICK_MEMBERS
                ) intersect it.permissions).isNotEmpty()
            }
            val guild = banner.guild.awaitSingle()
            val self = guild.selfMember.awaitSingle()
            val selfHighestRole = self.highestRole.awaitSingle()
            val cantRemove = toRemove.filter {
                OrderUtil.ROLE_ORDER.compare(selfHighestRole, it) <= 0
            }
            if (cantRemove.isNotEmpty()) return

            toRemove.map {
                banner.removeRole(it.id)
            }.whenComplete().awaitCompletion()

            val owner = guild.owner.awaitSingle()
            val ownerDms = owner.privateChannel.awaitSingle()
            ownerDms.sendEmbed {
                title("Ban Watch for server ${guild.name}")
                description("User ${banner.mention} was banning too fast and has had all roles allowing banning removed.")
            }

            val bannerDms = banner.privateChannel.awaitSingle()
            bannerDms.sendEmbed {
                title("Ban Watch for server ${guild.name}")
                description("You were banning too fast and have had all roles allowing banning removed. The owner of the server has also been informed. Please contact administrators to have these roles added back.")
            }
        }
    }
}