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

package vettingbot.gatewaysubscribers

import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.Event
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.whenComplete
import vettingbot.listeners.DiscordEventListener
import vettingbot.listeners.getEventType

private val logger = KotlinLogging.logger {}

@Component
class DiscordEventListenerSubscriber(
    private val listeners: List<DiscordEventListener<*>>
) : DiscordGatewaySubscriber {

    override fun subscribe(gateway: GatewayDiscordClient) {
        logger.debug { "Registering event listeners" }
        val myTypes = listeners.groupBy { getEventType(it) }
        myTypes.forEach { (eventType, eventListeners) ->
            gateway.on(eventType).flatMap { event ->
                mono {
                    for (eventListener in eventListeners) {
                        try {
                            logger.debug("Processing event $eventType for $eventListener")
                            @Suppress("UNCHECKED_CAST")
                            (eventListener as DiscordEventListener<Event>).on(event)
                            logger.debug("Processed event $eventType for $eventListener")
                        } catch (e: Exception) {
                            logger.error(e) { "Error while processing $eventType for $eventListener." }
                        }
                    }
                }
            }.then().subscribe()
        }
        logger.debug { "Registered ${listeners.size} event listeners" }
    }
}