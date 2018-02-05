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

package owl.game.output;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public final class AigFactory {
  /* To avoid constructing too many AIGs for the same function, we shall keep
   * track of those we have already constructed.
   */
  private final Map<Aig, Aig> cache = new HashMap<>();

  public AigFactory() {
    cache.put(Aig.FALSE, Aig.FALSE);
  }

  public LabelledAig getNode(int variable) {
    return createNode(variable);
  }

  public LabelledAig getTrue() {
    return not(new LabelledAig(Aig.FALSE));
  }

  public LabelledAig getFalse() {
    return new LabelledAig(Aig.FALSE);
  }

  public LabelledAig conjunction(LabelledAig left, LabelledAig right) {
    return createNode(left, right);
  }

  public LabelledAig cube(BitSet b) {
    LabelledAig product = getTrue();
    for (int i = 0; i <= b.size(); i++) {
      LabelledAig lit = b.get(i) ? getNode(i + 1) : not(getNode(i + 1));
      product = conjunction(product, lit);
    }
    return product;
  }

  public LabelledAig disjunction(LabelledAig left, LabelledAig right) {
    return createNode(left.flip(), right.flip()).flip();
  }

  public LabelledAig not(LabelledAig a) {
    return a.flip();
  }

  private LabelledAig createNode(int variable) {
    return new LabelledAig(makeUnique(new Aig(variable)));
  }

  private LabelledAig createNode(LabelledAig left, LabelledAig right) {
    LabelledAig realLeft;
    LabelledAig realRight;

    if (left.isNegated && left.equals(getTrue())) {
      realLeft = getFalse();
    } else if (left.isNegated && left.equals(getFalse())) {
      realLeft = getTrue();
    } else {
      realLeft = left;
    }

    if (right.isNegated && right.equals(getTrue())) {
      realRight = getFalse();
    } else if (right.isNegated && right.equals(getFalse())) {
      realRight = getTrue();
    } else {
      realRight = right;
    }

    return new LabelledAig(
      makeUnique(new Aig(realLeft.aig, realRight.aig, realLeft.isNegated, realRight.isNegated)));
  }

  private Aig makeUnique(Aig object) {
    Aig unique = cache.putIfAbsent(object, object);
    return unique == null ? object : unique;
  }
}
