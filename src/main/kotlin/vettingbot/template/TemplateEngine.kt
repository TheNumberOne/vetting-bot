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

package vettingbot.template

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty

fun <T> createObjectTemplate(dsl: TemplateBuilder.() -> T): Template<T> {
    return Template(MetaTemplateBuilder().apply { dsl() }.templateParams) {
        val m = mutableMapOf<String, Any?>()
        MapTemplateBuilder(m).dsl() to m
    }
}

abstract class AbstractTemplate {
    val builder = MetaTemplateBuilder()
    fun <T> param(description: String): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> =
        builder.param(description)
}

fun <T : AbstractTemplate> createTemplate(dsl: () -> T): Template<T> {
    return Template(dsl().builder.templateParams) {
        val result = dsl()
        result to result.builder.params
    }
}