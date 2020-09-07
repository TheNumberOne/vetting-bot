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

package vettingbot.util

import discord4j.rest.http.client.ClientException
import io.netty.handler.codec.http.HttpResponseStatus
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

suspend fun <T> Publisher<T>.awaitCompletion() {
    Mono.`when`(this).awaitFirstOrNull()
}

fun <T> Mono<T>.onDiscordNotFound(f: (ClientException) -> Mono<T>): Mono<T> {
    return onErrorResume { e ->
        if (e is ClientException && e.status == HttpResponseStatus.NOT_FOUND) {
            f(e)
        } else {
            Mono.error(e)
        }
    }
}

fun <T> Flux<T>.onDiscordNotFound(f: (ClientException) -> Publisher<T>): Flux<T> {
    return onErrorResume { e ->
        if (e is ClientException && e.status == HttpResponseStatus.NOT_FOUND) {
            f(e)
        } else {
            Mono.error(e)
        }
    }
}