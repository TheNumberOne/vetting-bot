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

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.shard.GatewayBootstrap
import discord4j.gateway.intent.Intent
import discord4j.gateway.intent.IntentSet
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

@Configuration
class DiscordConfiguration {
    @Bean
    fun configureDiscordClient(@Value("\${discord.bot.token}") token: String): DiscordClient {
        val builder = DiscordClientBuilder.create(token)
        return builder.build()
    }

    @Bean
    fun intents(): IntentSet {
        return IntentSet.of(Intent.GUILDS, Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES, Intent.GUILD_MESSAGE_REACTIONS)
    }

    @Bean
    fun beginLogin(discordClient: DiscordClient, intents: IntentSet?): Mono<GatewayDiscordClient> {
        var bootstrap = GatewayBootstrap.create(discordClient)
        if (intents != null) bootstrap = bootstrap.setEnabledIntents(intents)
        return bootstrap.login().cache()
    }
}