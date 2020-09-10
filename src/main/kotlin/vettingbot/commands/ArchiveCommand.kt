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
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.collect
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import reactor.kotlin.core.publisher.toFlux
import vettingbot.archival.ArchiveChannelService
import vettingbot.archival.DeleteChannelMessageService
import vettingbot.command.AbstractCommand
import vettingbot.util.*
import java.time.Duration
import java.util.stream.Collectors.toList

@Component
class ArchiveCommand(
    private val archiveChannelService: ArchiveChannelService,
    private val deleteChannelMessageService: DeleteChannelMessageService
) :
    AbstractCommand("archive", "Allows access to archives of vetting messages.", Permission.ADMINISTRATOR) {

    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
        description("This command allows administrators to browse messages created in vetting channels that have been deleted.")
        field(
            "Syntax", """
            `archive` - With no parameters, this gives a page of the most recent archives created or updated.
            `archive @user` - Recreates the vetting channel for `@user`. Only the runner of the command has access to the created channel by default.
            `archive userId` - Recreates the vetting channel for the user with the specified id.
        """.trimIndent()
        )
        field(
            "Examples", """
            `archive 87468527490569087` - Recreates the vetting channel for the user with id 87468527490569087.
            `archive @my_buddy` - Recreates the vetting channel for @my_buddy.
        """.trimIndent()
        )
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        val member = message.member.nullable ?: return
        if (args.isBlank()) {
            val sort = Sort.by(Sort.Direction.DESC, "lastMessageArchived")
            var archives = archiveChannelService.findArchives(
                guildId,
                PageRequest.of(0, 20, sort)
            )
            val people =
                archives.content.joinToString("\n") { it.memberMention() }.ifEmpty { "No one has been vetted yet." }
            val result = message.respondEmbed {
                title("Archives")
                description("To see the archive of an individual person, run `!archive @person` or `!archive personId`")
                field("Most recent", people)
                if (archives.totalPages > 1) {
                    footer("Page ${archives.number + 1}/${archives.totalPages}")
                }
            }
            if (archives.totalPages > 1) {
                val emojis = "⏪◀▶⏩❌".codePoints()
                    .mapToObj { ReactionEmoji.unicode(String(intArrayOf(it), 0, 1)) }
                    .collect(toList())

                for (emoji in emojis) {
                    result.addReaction(emoji).awaitCompletion()
                }

                message.client.on(ReactionAddEvent::class.java)
                    .filter {
                        it.messageId == result.id && it.userId == member.id && it.emoji in emojis
                    }
                    .timeout(Duration.ofMinutes(5))
                    .toFlux()
                    .takeWhile {
                        it.emoji.asUnicodeEmoji().get().raw != "❌"
                    }
                    .collect { reaction ->
                        coroutineScope {
                            launch {
                                result.removeReaction(reaction.emoji, member.id).awaitCompletion()
                            }
                            val page = when (reaction.emoji.asUnicodeEmoji().get().raw) {
                                "⏪" -> PageRequest.of(0, 20, sort)
                                "◀" -> archives.previousOrFirstPageable()
                                "▶" -> archives.nextOrLastPageable()
                                "⏩" -> PageRequest.of(archives.totalPages - 1, 20, sort)
                                else -> error("unexpected emoji")
                            }
                            archives = archiveChannelService.findArchives(guildId, page)
                            val people1 = archives.content.joinToString("\n") { it.memberMention() }
                                .ifEmpty { "No one has been vetted yet." }
                            result.edit { spec ->
                                spec.setEmbed(embedDsl {
                                    title("Archives")
                                    description("To see the archive of an individual person, run `!archive @person` or `!archive personId`")
                                    field("Most recent", people1)
                                    footer("Page ${archives.number + 1}/${archives.totalPages}")
                                })
                            }.awaitCompletion()
                        }
                    }

                result.removeAllReactions().awaitCompletion()
            }
            return
        }

        val user = findAndParseSnowflake(args) ?: run {
            message.respondEmbed {
                title("Archive")
                description("This is not a valid person. Make sure to @ the person or paste the id. Example usage `!archive @user` or `!archive user_id`")
            }
            return
        }
        val guild = message.guild.awaitSingle()
        val self = guild.selfMember.awaitSingle()
        val channel = guild.createTextChannel { spec ->
            spec.setName("archive-${user.asString()}")
            spec.setPermissionOverwrites(
                setOf(
                    PermissionOverwrite.forMember(
                        member.id, PermissionSet.of(
                            Permission.READ_MESSAGE_HISTORY, Permission.VIEW_CHANNEL, Permission.ADD_REACTIONS
                        ), PermissionSet.none()
                    ),
                    PermissionOverwrite.forRole(guildId, PermissionSet.none(), PermissionSet.all()),
                    PermissionOverwrite.forMember(self.id, PermissionSet.all(), PermissionSet.none())
                )
            )
        }.awaitSingle()
        archiveChannelService.restoreArchiveFor(user, channel)
        deleteChannelMessageService.addDeleteMessage(channel)
        channel.sendMessage {
            content(member.mention + " finished restoring archive.")
        }
    }
}