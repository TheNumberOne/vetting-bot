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

import discord4j.core.event.domain.message.MessageCreateEvent
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.util.respondEmbed

@Component
class SourceCommand: AbstractCommand("source", "Retrieve information regarding the source code of this bot.") {

    override suspend fun run(message: MessageCreateEvent, args: String) {
        message.respondEmbed {
            title("Source")
            description("""
                The source code and license of this bot are located at https://github.com/TheNumberOne/vetting-bot.
                
                Copyright (C) 2020 Rosetta Roberts <rosettafroberts@gmail.com>.

                VettingBot is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
                
                VettingBot is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
                
                You should have received a copy of the GNU Affero General Public License along with VettingBot. If not, see <https://www.gnu.org/licenses/>.
            """.trimIndent())
        }
    }
}