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

package vettingbot.configuration

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import vettingbot.util.wrapExceptions

@Component
class BotConfigService(
    private val defaultBotConfig: BotConfig,
    private val repository: BotConfigRepository,
    private val trans: TransactionalOperator
) {
    private suspend fun getConfig(): BotConfig {
        return wrapExceptions {
            trans.executeAndAwait {
                repository.findById(BotConfig.INSTANCE_ID).awaitFirstOrNull() ?: repository.save(defaultBotConfig)
                    .awaitSingle()
            }!!
        }
    }

    suspend fun getDefaultPrefix(): String {
        return getConfig().defaultPrefix
    }

    suspend fun getDefaultCategoryName(): String {
        return getConfig().defaultCategoryName
    }

    suspend fun getDefaultVettingText(): String {
        return getConfig().defaultVettingText
    }
}