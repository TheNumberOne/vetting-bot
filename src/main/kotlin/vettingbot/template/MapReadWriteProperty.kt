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

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class MapReadWriteProperty(val map: MutableMap<String, Any?>) : ReadWriteProperty<Any?, Any?> {
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Any?) {
        map[property.name.toLowerCase()] = value
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Any? {
        return map[property.name.toLowerCase()]
    }
}