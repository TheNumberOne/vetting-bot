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
import discord4j.rest.util.Permission
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.guild.GuildConfigService
import vettingbot.template.showValidation
import vettingbot.util.embedDsl
import vettingbot.util.nullable
import vettingbot.util.respondEmbed
import vettingbot.vetting.vetMessageTemplate

@Component
class VetMessageCommand(
    private val guildConfigService: GuildConfigService
) : AbstractCommand(
    listOf("vetmsg", "vetmessage", "vetting"),
    "View or change the message sent to members when they begin the vetting process.",
    Permission.ADMINISTRATOR
) {
    override suspend fun displayHelp(guildId: Snowflake) = embedDsl {
        field(
            "Command Syntax", """
            `vetmsg` - Displays the message sent to members when they start the vetting process.
            `vetmsg message` - Sets message sent to members when they start the vetting process. This message can span multiple lines.
        """.trimIndent()
        )
        field("Message Syntax", "`{member}` is replaced with a mention of the vetting member.")
        field(
            "Example Usage", """
            `vetmsg Hello! Welcome to this server.` - Displays "Hello! Welcome to this server." when members begin the vetting process.
            `vetmsg Hello {member}.` - Mentions the member when it greets the member.
        """.trimIndent()
        )
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        val vettingText = guildConfigService.getVettingText(guildId)
        if (args.isBlank()) {
            message.respondEmbed {
                title("Vetting Text")
                description(vettingText)
            }
        } else {
            vetMessageTemplate.validate(args)?.let { validationResult ->
                message.respondEmbed {
                    showValidation(vetMessageTemplate, args, validationResult)
                }
                return
            }
            guildConfigService.setVettingText(guildId, args)
            message.respondEmbed {
                title("Vetting Text")
                description("Changed vetting text.")
                field("Before", vettingText)
                field("After", args)
            }
        }
    }
}