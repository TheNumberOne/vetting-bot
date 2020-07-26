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

package vettingbot.discord

import discord4j.core.event.domain.Event
import java.lang.reflect.ParameterizedType

interface DiscordEventListener<T> where T : Event {
    suspend fun on(event: T)

    fun getEventType(): Class<T> {
        val interfaces = javaClass.genericInterfaces
        val type = interfaces.filterIsInstance<ParameterizedType>().single {
            it.rawType == DiscordEventListener::class.java
        }
        val (argument) = type.actualTypeArguments

        require(argument is Class<*>) {
            "Type parameter of ${DiscordEventListener<*>::javaClass.name} for ${javaClass.name} must be a concrete type."
        }

        @Suppress("UNCHECKED_CAST")
        return argument as Class<T>
    }
}
