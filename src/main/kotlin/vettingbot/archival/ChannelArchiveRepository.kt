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

package vettingbot.archival

import discord4j.common.util.Snowflake
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface ChannelArchiveRepository : ReactiveCrudRepository<ChannelArchive, Long> {
    suspend fun findByGuildIdAndUserId(guildId: Snowflake, userId: Snowflake): ChannelArchive?
    suspend fun countByGuildId(guildId: Snowflake): Long
    fun findByGuildId(guildId: Snowflake, page: Pageable): Flow<ChannelArchive>
}