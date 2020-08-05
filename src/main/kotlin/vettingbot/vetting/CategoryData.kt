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
import discord4j.core.`object`.entity.channel.Category
import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.schema.Relationship
import org.springframework.data.annotation.PersistenceConstructor

@Node
data class CategoryData @PersistenceConstructor constructor(
    // category id
    @Id val id: Long,
    val guildId: Snowflake,
    @Relationship("PARENT_OF")
    val vettingChannels: Set<VettingChannel> = emptySet()
) {
    constructor(channelId: Snowflake, guildId: Snowflake, vettingChannels: Set<VettingChannel> = emptySet()):
            this(channelId.asLong(), guildId, vettingChannels)
    constructor(category: Category): this(category.id, category.guildId)
}