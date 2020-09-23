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


import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private val exampleTemplate = createObjectTemplate {
    object {
        var t by param<String>("The t variable")
    }
}

class TemplateEngineKtTest : StringSpec({
    "Creating a simple template should work" {
        exampleTemplate.expand("{ t  } World") {
            t = "Hello"
        } shouldBe "Hello World"
    }
    "{{ should work" {
        exampleTemplate.expand("{{ World") { } shouldBe "{ World"
    }
    "}} should work" {
        exampleTemplate.expand("{{ Wo}}rld") { } shouldBe "{ Wo}rld"
    }
    "Should detect no parameter" {
        exampleTemplate.validate("Hello {  } World") shouldBe TemplateValidationResult(
            6, 10,
            TemplateValidationResult.Type.NoParameter
        )
    }
    "Should detect nested curly brace" {
        exampleTemplate.validate("Hello { {} } World") shouldBe TemplateValidationResult(
            8,
            9,
            TemplateValidationResult.Type.NestedCurlyBrace
        )
    }
    "Should detect invalid parameter" {
        exampleTemplate.validate("Hello {world}") shouldBe TemplateValidationResult(
            6,
            13,
            TemplateValidationResult.Type.InvalidParameter
        )
    }
    "Should detect unmatched left brace" {
        exampleTemplate.validate("Hello { world") shouldBe TemplateValidationResult(
            6,
            13,
            TemplateValidationResult.Type.UnmatchedLeftCurlyBrace
        )
    }
    "Should detect unmatched right brace" {
        exampleTemplate.validate("Hello } world") shouldBe TemplateValidationResult(
            0,
            7,
            TemplateValidationResult.Type.UnmatchedRightCurlyBrace
        )
    }
    "Should detect unmatched left brace at start" {
        exampleTemplate.validate("{Hello world") shouldBe TemplateValidationResult(
            0,
            12,
            TemplateValidationResult.Type.UnmatchedLeftCurlyBrace
        )
    }
    "Should detect unmatched right brace at start" {
        exampleTemplate.validate("}Hello world") shouldBe TemplateValidationResult(
            0,
            1,
            TemplateValidationResult.Type.UnmatchedRightCurlyBrace
        )
    }
    "Should detect unmatched left brace at end" {
        exampleTemplate.validate("Hello world{") shouldBe TemplateValidationResult(
            11,
            12,
            TemplateValidationResult.Type.UnmatchedLeftCurlyBrace
        )
    }
    "Should detect unmatched right brace at end" {
        exampleTemplate.validate("Hello world}") shouldBe TemplateValidationResult(
            0,
            12,
            TemplateValidationResult.Type.UnmatchedRightCurlyBrace
        )
    }
    "Error range of unmatched right brace should not extend to include template" {
        exampleTemplate.validate("Hello {t}world}") shouldBe TemplateValidationResult(
            9,
            15,
            TemplateValidationResult.Type.UnmatchedRightCurlyBrace
        )
    }
})