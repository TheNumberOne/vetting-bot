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

import discord4j.core.GatewayDiscordClient
import org.springframework.boot.CommandLineRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.Disposables
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps the application alive while the gateway is active.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class GatewayRunner(
        private val gatewayMono: Mono<GatewayDiscordClient>,
        private val subscribers: List<DiscordGatewaySubscriber>
) : CommandLineRunner {
    private val cancel: AtomicReference<Disposable?> = AtomicReference()

    override fun run(vararg args: String?) {
        start()
    }

    fun start() {
        val scheduler = Schedulers.newParallel(
                "Vetting Bot",
                Schedulers.DEFAULT_POOL_SIZE,
                false
        )

        val cancelMono = gatewayMono.doOnNext { gateway ->
            subscribers.forEach {
                it.subscribe(gateway)
            }
        }.flatMap { it.onDisconnect() }.subscribeOn(scheduler).cache().subscribe()

        cancel.getAndSet(Disposables.composite(scheduler, cancelMono))?.dispose()
    }

    fun stop() {
        cancel.getAndSet(null)?.dispose()
    }
}