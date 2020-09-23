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

class Template<T>(
    val params: List<TemplateParam>,
    private val factory: () -> Pair<T, Map<String, Any?>>
) {
    private val namesLowerToParams = params.map { it.name.toLowerCase() to it }.toMap()

    fun expand(text: String, properties: T.() -> Unit): String {
        val (instance, actualParams) = factory()
        instance.apply(properties)
        return expand(text, actualParams)
    }

    private sealed class ExpandState {
        class ReadingText(val start: Int) : ExpandState()
        class ReadStartCurlyBrace(val textStart: Int) : ExpandState()
        class ReadingVariable(val start: Int) : ExpandState()
        class ReadEndCurlyBraceInText(val textStart: Int) : ExpandState()
    }

    private fun expand(text: String, properties: Map<String, Any?>): String {
        var state: ExpandState = ExpandState.ReadingText(0)
        val builder = StringBuilder()
        for ((i, c) in text.withIndex()) {
            state = when (state) {
                is ExpandState.ReadingText -> when (c) {
                    '{' -> ExpandState.ReadStartCurlyBrace(state.start)
                    '}' -> ExpandState.ReadEndCurlyBraceInText(state.start)
                    else -> state
                }
                is ExpandState.ReadStartCurlyBrace -> when (c) {
                    '{' -> {
                        builder.append(text, state.textStart, i)
                        ExpandState.ReadingText(i + 1)
                    }
                    '}' -> throw IllegalArgumentException("No parameter name between curly braces.")
                    else -> {
                        builder.append(text, state.textStart, i - 1)
                        ExpandState.ReadingVariable(i)
                    }
                }
                is ExpandState.ReadingVariable -> when (c) {
                    '{' -> throw IllegalArgumentException("Can't have a nested curly brace.")
                    '}' -> {
                        val variableName = text.substring(state.start until i).trim().toLowerCase()
                        if (variableName.isEmpty()) {
                            throw IllegalArgumentException("No parameter name between curly braces.")
                        }
                        if (variableName !in properties) {
                            throw IllegalArgumentException("Invalid parameter name between curly braces.")
                        }
                        val property = properties[variableName].toString()
                        builder.append(property)
                        ExpandState.ReadingText(i + 1)
                    }
                    else -> state
                }
                is ExpandState.ReadEndCurlyBraceInText -> when (c) {
                    '}' -> {
                        builder.append(text, state.textStart, i)
                        ExpandState.ReadingText(i + 1)
                    }
                    else -> throw IllegalArgumentException("Can't have an unmatched }.")
                }
            }
        }
        when (state) {
            is ExpandState.ReadingText -> {
                builder.append(text, state.start, text.length)
            }
            is ExpandState.ReadStartCurlyBrace, is ExpandState.ReadingVariable -> {
                throw IllegalArgumentException("Can't have an unmatched {")
            }
            is ExpandState.ReadEndCurlyBraceInText -> {
                throw IllegalArgumentException("Can't have an unmatched }.")
            }
        }
        return builder.toString()
    }

    fun validate(text: String): TemplateValidationResult? {
        var state: ExpandState = ExpandState.ReadingText(0)
        for ((i, c) in text.withIndex()) {
            state = when (state) {
                is ExpandState.ReadingText -> when (c) {
                    '{' -> ExpandState.ReadStartCurlyBrace(state.start)
                    '}' -> ExpandState.ReadEndCurlyBraceInText(state.start)
                    else -> state
                }
                is ExpandState.ReadStartCurlyBrace -> when (c) {
                    '{' -> {
                        ExpandState.ReadingText(i + 1)
                    }
                    '}' -> return TemplateValidationResult(
                        state.textStart,
                        i + 1,
                        TemplateValidationResult.Type.NoParameter
                    )
                    else -> {
                        ExpandState.ReadingVariable(i)
                    }
                }
                is ExpandState.ReadingVariable -> when (c) {
                    '{' -> return TemplateValidationResult(i, i + 1, TemplateValidationResult.Type.NestedCurlyBrace)
                    '}' -> {
                        val variableName = text.substring(state.start until i).trim().toLowerCase()
                        if (variableName.isEmpty()) {
                            return TemplateValidationResult(
                                state.start - 1,
                                i + 1,
                                TemplateValidationResult.Type.NoParameter
                            )
                        }
                        if (variableName !in namesLowerToParams) {
                            return TemplateValidationResult(
                                state.start - 1,
                                i + 1,
                                TemplateValidationResult.Type.InvalidParameter
                            )
                        }
                        ExpandState.ReadingText(i + 1)
                    }
                    else -> state
                }
                is ExpandState.ReadEndCurlyBraceInText -> when (c) {
                    '}' -> {
                        ExpandState.ReadingText(i + 1)
                    }
                    else -> return TemplateValidationResult(
                        state.textStart,
                        i,
                        TemplateValidationResult.Type.UnmatchedRightCurlyBrace
                    )
                }
            }
        }
        when (state) {
            is ExpandState.ReadingText -> {
            }
            is ExpandState.ReadStartCurlyBrace -> {
                return TemplateValidationResult(
                    text.lastIndex,
                    text.length,
                    TemplateValidationResult.Type.UnmatchedLeftCurlyBrace
                )
            }
            is ExpandState.ReadingVariable -> {
                return TemplateValidationResult(
                    state.start - 1,
                    text.length,
                    TemplateValidationResult.Type.UnmatchedLeftCurlyBrace
                )
            }
            is ExpandState.ReadEndCurlyBraceInText -> {
                return TemplateValidationResult(
                    state.textStart,
                    text.length,
                    TemplateValidationResult.Type.UnmatchedRightCurlyBrace
                )
            }
        }
        return null
    }
}