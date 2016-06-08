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

import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAIntermediate;

import java.util.List;

public class RemoveComments extends HOAIntermediate {
    public RemoveComments(HOAConsumer consumer) {
        super(consumer);
    }

    @Override
    public void setName(String s) {

    }

    @Override
    public void addState(int var1, String var2, BooleanExpression<AtomLabel> var3, List<Integer> var4) throws HOAConsumerException {
        super.addState(var1, null, var3, var4);
    }
}
