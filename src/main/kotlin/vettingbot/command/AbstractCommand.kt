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

package vettingbot.command

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.reactive.awaitSingle
import vettingbot.util.respondEmbed

@Suppress("SpringJavaConstructorAutowiringInspection")
open class AbstractCommand(
    final override val names: List<String>,
    final override val quickHelp: String,
    private val permissionsRequired: PermissionSet = PermissionSet.none()
) : Command {
    constructor(
        name: String,
        quickHelp: String,
        vararg permissionsRequired: Permission
    ) : this(
        listOf(name),
        quickHelp,
        PermissionSet.of(*permissionsRequired)
    )

    constructor(
        names: List<String>,
        quickHelp: String,
        vararg permissionsRequired: Permission
    ) : this(names, quickHelp, PermissionSet.of(*permissionsRequired))

    override val subCommands: List<Command> = emptyList()

    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = { }

    override suspend fun canExecute(guildId: Snowflake, member: Member): Boolean {
        return !member.isBot &&
                (permissionsRequired.isEmpty() ||
                        member.basePermissions.awaitSingle().containsAll(permissionsRequired))
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        message.respondEmbed {
            description("This command is not yet implemented.")
        }
    }
}