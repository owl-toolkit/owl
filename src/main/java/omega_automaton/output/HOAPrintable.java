/*
 * Copyright (C) 2016  (See AUTHORS)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omega_automaton.output;

import jhoafparser.consumer.HOAConsumer;

import java.util.EnumSet;
import java.util.Map;

public interface HOAPrintable {

    enum Option {
        ANNOTATIONS
    }

    default void toHOA(HOAConsumer consumer) {
        toHOA(consumer, EnumSet.noneOf(Option.class));
    }

    void toHOA(HOAConsumer consumer, EnumSet<Option> options);

    void setAtomMapping(Map<Integer, String> mapping);
}
