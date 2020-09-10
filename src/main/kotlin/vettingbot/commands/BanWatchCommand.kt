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

package vettingbot.commands

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import org.springframework.stereotype.Component
import vettingbot.banwatch.BanWatchService
import vettingbot.command.AbstractCommand
import vettingbot.util.*

@Component
class BanWatchCommand(private val banWatchService: BanWatchService) :
    AbstractCommand(
        "banwatch",
        "Allows monitoring bans and kicks on this server to prevent rogue moderators.",
        Permission.ADMINISTRATOR
    ) {
    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit {
        return embedDsl {
            description(
                """
                Ban Watch is a feature that removes moderator roles from users who ban or kick too many people. This is useful in the event a server is hacked or if a moderator goes rogue.
                
                **WARNING: This bot cannot remove moderator roles that are above all this bot's roles.**
            """.trimIndent()
            )
            field(
                "Syntax", """
                `banwatch` - Displays if ban watch is enabled, along with relevant settings.
                `banwatch time n` - Enables ban watch if not previously enabled, and configures it to remove moderator roles from a user if they ban or kick `n` users within `time`. For help on how to format the time parameter, run `help time.`
                `banwatch disable` - Disables ban watch.
            """.trimIndent()
            )
            field(
                "Example usage",
                "`banwatch 30m 5` — Configures ban watches to remove moderators who remove or kick more than 5 people within 30 minutes."
            )
        }
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        if (args.isEmpty()) {
            val settings = banWatchService.find(guildId)
            if (settings == null) {
                message.respondEmbed {
                    title("Ban Watch")
                    description("Ban watch is currently disabled for this server.")
                }
            } else {
                message.respondEmbed {
                    title("Ban Watch")
                    description(
                        "Ban watch is currently enabled. Moderator roles are removed if they remove more than ${settings.maxBans} members from this server in ${settings.interval.toAbbreviatedString()}.\n" +
                                "**WARNING: This bot cannot remove moderator roles that are above all this bot's roles.**"
                    )
                }
            }
            return
        }

        if (args == "disable") {
            banWatchService.disable(guildId)
            message.respondEmbed { description("Disabled ban watch.") }
        }

        val parts = args.split(" ")
        if (parts.size != 2) {
            message.respondEmbed {
                title("Ban Watch")
                description("Expected two arguments.")
            }
            return
        }
        val (durationStr, banLimitStr) = parts
        val duration = parseDuration(durationStr) ?: run {
            message.respondEmbed {
                title("Ban Watch")
                description("The first argument was not recognized as a length of time.")
                displayDurationHelp()
            }
            return
        }
        val banLimit = banLimitStr.toIntOrNull() ?: run {
            message.respondEmbed {
                title("Ban Watch")
                description("The second argument was not recognized as a number.")
            }
            return
        }
        banWatchService.enableBanWatch(guildId, duration, banLimit)
        message.respondEmbed {
            title("Ban Watch")
            description(
                "Ban watch was enabled for this server. Moderator roles are removed if they remove more than $banLimit members from this server in ${duration.toAbbreviatedString()}.\n" +
                        "**WARNING: This bot cannot remove moderator roles that are above all this bot's roles.**"
            )
        }
    }
}