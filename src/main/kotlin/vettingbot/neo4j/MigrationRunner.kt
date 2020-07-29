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

package vettingbot.neo4j

import org.liquigraph.core.api.Liquigraph
import org.liquigraph.core.configuration.ConfigurationBuilder
import org.neo4j.driver.springframework.boot.autoconfigure.Neo4jDriverProperties
import org.springframework.boot.CommandLineRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MigrationRunner(private val neo4JProperties: Neo4jDriverProperties) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val configuration = ConfigurationBuilder()
                .withMasterChangelogLocation("db/liquigraph/changelog.xml")
                .withUri("jdbc:neo4j:" + neo4JProperties.uri.toString())
                .withUsername(neo4JProperties.authentication.username)
                .withPassword(neo4JProperties.authentication.password)
                .withRunMode()
                .build()

        Liquigraph().runMigrations(configuration)
    }
}