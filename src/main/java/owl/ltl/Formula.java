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

package owl.ltl;

import java.util.BitSet;
import java.util.function.Predicate;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

public interface Formula {

  int accept(IntVisitor visitor);

  <R> R accept(Visitor<R> visitor);

  <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter);

  boolean allMatch(Predicate<Formula> predicate);

  boolean anyMatch(Predicate<Formula> predicate);

  // Temporal Properties of an LTL Formula
  boolean isPureEventual();

  boolean isPureUniversal();

  boolean isSuspendable();

  /**
   * Syntactically negate this formula.
   *
   * @return The negation of this formula in NNF.
   */
  Formula not();

  /**
   * Do a single temporal step. This means that one layer of X-operators is removed and literals are
   * replaced by their valuations.
   */
  Formula temporalStep(BitSet valuation);

  /**
   * Short-cut operation to avoid intermediate construction of formula ASTs.
   */
  Formula temporalStepUnfold(BitSet valuation);

  Formula unfold();

  /**
   * Short-cut operation to avoid intermediate construction of formula ASTs.
   */
  Formula unfoldTemporalStep(BitSet valuation);
}
