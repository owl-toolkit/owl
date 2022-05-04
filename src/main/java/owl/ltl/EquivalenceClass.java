/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer)
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

package owl.ltl;

import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.MtBdd;

/**
 * A propositional equivalence class of an LTL formula.
 *
 * @implSpec If two implementing objects were created by different factories, methods combining or
 * comparing these objects are allowed to throw exceptions.
 */
public interface EquivalenceClass extends LtlLanguageExpressible {

  /**
   * Collects all literals used in the bdd and stores the corresponding atomic propositions in the
   * BitSet. See also {@link Formula#atomicPropositions(boolean)}.
   */
  default BitSet atomicPropositions(boolean includeNested) {
    BitSet atomicPropositions = new BitSet();

    for (Formula formula : support(includeNested)) {
      if (formula instanceof Literal) {
        atomicPropositions.set(((Literal) formula).getAtom());
      }
    }

    return atomicPropositions;
  }

  /**
   * The canonical representative for this equivalence class, which is defined as the formula
   * representation of the {@link EquivalenceClass#conjunctiveNormalForm}.
   *
   * @return The canonical representative.
   */
  default Formula canonicalRepresentativeCnf() {
    return Conjunction.of(conjunctiveNormalForm().stream().map(Disjunction::of));
  }

  /**
   * The canonical representative for this equivalence class, which is defined as the formula
   * representation of the {@link EquivalenceClass#disjunctiveNormalForm}.
   *
   * @return The canonical representative.
   */
  default Formula canonicalRepresentativeDnf() {
    return Disjunction.of(disjunctiveNormalForm().stream().map(Conjunction::of));
  }

  Set<Set<Formula>> conjunctiveNormalForm();

  Set<Set<Formula>> disjunctiveNormalForm();

  EquivalenceClassFactory factory();

  boolean isFalse();

  boolean isTrue();

  /**
   * A sorted, distinct list of formula objects ({@link Literal} and
   * {@link owl.ltl.Formula.TemporalOperator}) that are used as propositions in the support of the
   * backing BDD.
   *
   * @param includeNested include also nested subformulas.
   * @return sorted, distinct, and unmodifiable list.
   */
  List<Formula> support(boolean includeNested);

  default Set<Formula.TemporalOperator> temporalOperators() {
    return temporalOperators(false);
  }

  Set<Formula.TemporalOperator> temporalOperators(boolean includeNested);

  boolean implies(EquivalenceClass other);

  EquivalenceClass and(EquivalenceClass other);

  EquivalenceClass or(EquivalenceClass other);

  /**
   * See {@link Formula#substitute(Function)}.
   *
   * @param substitution The substitution function. It is only called on modal / temporal
   *                     operators.
   */
  EquivalenceClass substitute(
      Function<? super Formula.TemporalOperator, ? extends Formula> substitution);

  /**
   * See {@link Formula#temporalStep(BitSet)}.
   *
   * @param valuation The assignment for the atomic propositions.
   */
  default EquivalenceClass temporalStep(BitSet valuation) {
    return temporalStepTree().get(valuation).iterator().next();
  }

  MtBdd<EquivalenceClass> temporalStepTree();

  /**
   * See {@link Formula#unfold()}.
   */
  EquivalenceClass unfold();

  double trueness();

  EquivalenceClass not();

  EquivalenceClassFactory.Encoding encoding();

  EquivalenceClass encode(EquivalenceClassFactory.Encoding encoding);

  @Override
  default EquivalenceClass language() {
    return this;
  }
}
