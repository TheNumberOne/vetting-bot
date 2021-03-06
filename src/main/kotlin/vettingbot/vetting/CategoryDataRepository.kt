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

import discord4j.common.util.Snowflake
import org.springframework.data.neo4j.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux

interface CategoryDataRepository :
    ReactiveCrudRepository<CategoryData, Long>
{
    @Query("MATCH (c:CategoryData { guildId: \$guildId })\n" +
            "OPTIONAL MATCH (c)-[:PARENT_OF]->(v:VettingChannel)\n" +
            "WITH c, count(v) as numChildren\n" +
            "WHERE numChildren < \$limit\n" +
            "RETURN c\n"
    )
    fun findNonFullCategoriesByGuildId(guildId: Snowflake, limit: Int): Flux<CategoryData>

    fun findByGuildId(guildId: Snowflake): Flux<CategoryData>
}