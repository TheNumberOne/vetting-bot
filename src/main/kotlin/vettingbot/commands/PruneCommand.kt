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
import vettingbot.command.AbstractCommand
import vettingbot.purge.PruneService
import vettingbot.util.embedDsl
import vettingbot.util.nullable
import vettingbot.util.respondEmbed

@Component
class PruneCommand(private val pruneService: PruneService) : AbstractCommand(
    "prune",
    "Manages automatic kicking of users who have not completed the vetting process and not sent any recent messages.",
    Permission.ADMINISTRATOR
) {
    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
        field(
            "Syntax", """
            `prune` - Displays the current settings for the prune command.
            `prune disable` - Stops pruning users.
            `prune n` - Kicks users who have not sent any messages for `n` days and are not vetted. `n` must be between 1 and 30 inclusive.
        """.trimIndent()
        )
        field(
            "Example Usage", """
            `prune 5` - Kicks users who have not sent any messages for 5 days and have not completed the vetting process.
        """.trimIndent()
        )
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        val days = pruneService.findSchedule(guildId)
        if (args.isEmpty()) {
            if (days == null) {
                message.respondEmbed {
                    description("Automatic pruning of inactive non vetted people is disabled.")
                }
            } else {
                message.respondEmbed {
                    description("Users who have not sent any messages for $days days and have not completed the vetting process are automatically kicked.")
                }
            }
            return
        }
        if (args == "disable") {
            if (days == null) {
                message.respondEmbed {
                    description("Automatic pruning of inactive non vetted people is already disabled.")
                }
            } else {
                pruneService.removeSchedule(guildId)
                message.respondEmbed {
                    description("Automatic pruning of inactive non vetted people has been disabled.")
                }
            }
            return
        }

        val newDays = args.toIntOrNull()
        if (newDays == null || newDays < 1 || newDays > 30) {
            message.respondEmbed {
                description("An invalid number was passed for the first parameter. Make sure the first parameter is a number between 1 and 30 inclusive.")
            }
            return
        }

        pruneService.schedule(guildId, newDays)
        message.respondEmbed {
            description("Users who have not sent any messages for $newDays days and have not completed the vetting process are now automatically kicked.")
        }
    }
}