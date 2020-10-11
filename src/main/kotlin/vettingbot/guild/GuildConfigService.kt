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

package vettingbot.guild

import discord4j.common.util.Snowflake
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import vettingbot.configuration.BotConfigService
import vettingbot.util.awaitCompletion
import vettingbot.util.wrapExceptions

@Component
class GuildConfigService(private val repo: GuildConfigRepository, private val botConfig: BotConfigService) {
    suspend fun getGuildConfig(id: Snowflake): GuildConfig {
        return wrapExceptions {
            repo.findById(id).awaitFirstOrNull()
        } ?: wrapExceptions {
            repo.save(GuildConfig(id, botConfig.getDefaultPrefix(), botConfig.getDefaultVettingText())).awaitSingle()
        }
    }

    suspend fun getPrefix(guildId: Snowflake): String {
        return getGuildConfig(guildId).prefix
    }

    suspend fun setPrefix(guildId: Snowflake, prefix: String) {
        getGuildConfig(guildId).copy(prefix = prefix).also {
            wrapExceptions {
                repo.save(it).awaitCompletion()
            }
        }
    }

    suspend fun getVettingText(guildId: Snowflake): String {
        return getGuildConfig(guildId).vettingText
    }

    suspend fun setVettingText(guildId: Snowflake, text: String) {
        getGuildConfig(guildId).copy(vettingText = text).also {
            wrapExceptions {
                repo.save(it).awaitCompletion()
            }
        }
    }

    suspend fun isEnabled(guildId: Snowflake): Boolean {
        return getGuildConfig(guildId).enabled
    }

    suspend fun setEnabled(guildId: Snowflake, enabled: Boolean) {
        val config = getGuildConfig(guildId)
        if (config.enabled == enabled) return
        val newConfig = config.copy(enabled = enabled)
        if (!enabled || canEnable(config)) {
            wrapExceptions {
                repo.save(newConfig).awaitCompletion()
            }
        } else {
            throw IllegalStateException("Can't enable the guild.")
        }
    }

    private fun canEnable(config: GuildConfig): Boolean {
        return config.vettedRole != null && config.vettingRole != null
    }

    suspend fun getVettingRole(guildId: Snowflake): Snowflake? {
        return getGuildConfig(guildId).vettingRole
    }

    suspend fun setVettingRole(guildId: Snowflake, roleId: Snowflake) {
        return getGuildConfig(guildId).copy(vettingRole = roleId).let {
            wrapExceptions {
                repo.save(it).awaitCompletion()
            }
        }
    }

    suspend fun getVettedRole(guildId: Snowflake): Snowflake? {
        return getGuildConfig(guildId).vettedRole
    }

    suspend fun setVettedRole(guildId: Snowflake, roleId: Snowflake) {
        return getGuildConfig(guildId).copy(vettedRole = roleId).let {
            wrapExceptions {
                repo.save(it).awaitCompletion()
            }
        }
    }
}