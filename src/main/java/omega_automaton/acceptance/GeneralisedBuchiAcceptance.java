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

package omega_automaton.acceptance;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnegative;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import omega_automaton.output.HOAConsumerExtended;

public class GeneralisedBuchiAcceptance implements OmegaAcceptance {

  @Nonnegative
  public final int size;

  public GeneralisedBuchiAcceptance(int size) {
    this.size = size;
  }

  @Override
  public int getAcceptanceSets() {
    return size;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    BooleanExpression<AtomAcceptance> conjunction = HOAConsumerExtended.mkInf(0);

    for (int i = 1; i < size; i++) {
      conjunction = conjunction.and(HOAConsumerExtended.mkInf(i));
    }

    return conjunction;
  }

  @Override
  public String getName() {
    return "generalized-Buchi";
  }

  @Override
  public List<Object> getNameExtra() {
    return Collections.singletonList(size);
  }

  public int getSize() {
    return size;
  }
}
