/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.automaton.acceptance;

import static jhoafparser.extensions.BooleanExpressions.createConjunction;

import java.util.BitSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;

public class GeneralizedBuchiAcceptance extends OmegaAcceptance {
  @Nonnegative
  public final int size;

  GeneralizedBuchiAcceptance(int size) {
    this.size = size;
  }

  public static GeneralizedBuchiAcceptance of(int size) {
    return size == 1 ? BuchiAcceptance.INSTANCE : new GeneralizedBuchiAcceptance(size);
  }

  @Override
  public final int acceptanceSets() {
    return size;
  }

  @Override
  public final BooleanExpression<AtomAcceptance> booleanExpression() {
    return createConjunction(IntStream.range(0, size).mapToObj(BooleanExpressions::mkInf));
  }

  @Override
  public String name() {
    return "generalized-Buchi";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of(size);
  }

  @Override
  public BitSet acceptingSet() {
    BitSet set = new BitSet();
    set.set(0, size);
    return set;
  }

  @Override
  public BitSet rejectingSet() {
    if (size == 0) {
      throw new NoSuchElementException();
    }

    return new BitSet();
  }
}
