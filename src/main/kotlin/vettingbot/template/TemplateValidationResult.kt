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

import vettingbot.util.EmbedCreateSpecDsl

data class TemplateValidationResult(val startIndex: Int, val endIndex: Int, val type: Type) {
    enum class Type {
        NoParameter,
        NestedCurlyBrace,
        InvalidParameter,
        UnmatchedLeftCurlyBrace,
        UnmatchedRightCurlyBrace
    }
}

fun EmbedCreateSpecDsl.showValidation(template: Template<*>, text: String, validationResult: TemplateValidationResult) {
    title("Invalid Template")
    description(
        when (validationResult.type) {
            TemplateValidationResult.Type.NoParameter -> "Missing template parameter name between curly braces."
            TemplateValidationResult.Type.NestedCurlyBrace -> "Nested template parameter."
            TemplateValidationResult.Type.InvalidParameter -> "Invalid parameter."
            TemplateValidationResult.Type.UnmatchedLeftCurlyBrace -> "Unmatched {"
            TemplateValidationResult.Type.UnmatchedRightCurlyBrace -> "Unmatched }"
        }
    )
    field(
        "Highlighted Problem",
        text.substring(0, validationResult.startIndex) + "`" + text.substring(
            validationResult.startIndex,
            validationResult.endIndex
        ) + "`" + text.substring(validationResult.endIndex)
    )
    field("Allowed Parameters", template.params.joinToString("\n") { it.name + "—" + it.description })
}