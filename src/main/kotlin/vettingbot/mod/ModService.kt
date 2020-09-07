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

package vettingbot.mod

import discord4j.common.util.Snowflake
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import vettingbot.util.awaitCompletion

@Component
class ModService(private val guildModsRepository: GuildModsRepository, private val trans: TransactionalOperator) {
    suspend fun getModRoles(guildId: Snowflake): Set<Snowflake> {
        return guildModsRepository.findById(guildId).awaitFirstOrNull()?.roleIds ?: emptySet()
    }

    suspend fun addModRoles(guildId: Snowflake, roles: Collection<Snowflake>) {
        trans.executeAndAwait {
            val mods = guildModsRepository.findById(guildId).awaitFirstOrNull() ?: GuildMods(guildId, emptySet())
            val edited = mods.copy(roleIds = mods.roleIds + roles)
            guildModsRepository.save(edited).awaitCompletion()
        }
    }

    suspend fun removeModRoles(guildId: Snowflake, roles: Collection<Snowflake>) {
        trans.executeAndAwait {
            val mods = guildModsRepository.findById(guildId).awaitFirstOrNull() ?: return@executeAndAwait
            val edited = mods.copy(roleIds = mods.roleIds - roles)
            guildModsRepository.save(edited).awaitCompletion()
        }
    }
}