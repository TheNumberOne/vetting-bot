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

package vettingbot.runners

import discord4j.core.GatewayDiscordClient
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import vettingbot.gatewaysubscribers.DiscordGatewaySubscriber

@Component
class SubscribeToGateway(
    private val gatewayMono: Mono<GatewayDiscordClient>,
    private val subscribers: List<DiscordGatewaySubscriber>
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        gatewayMono.doOnNext { gateway ->
            subscribers.forEach {
                it.subscribe(gateway)
            }
        }.flatMap { it.onDisconnect() }.block()
    }
}