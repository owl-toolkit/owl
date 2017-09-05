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

package owl.translations.frequency;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;
import owl.translations.frequency.ProductControllerSynthesis.State;

class AccLocalControllerSynthesis {
  /* private static final Predicate<Formula> INFINITY_OPERATORS = x -> x instanceof FrequencyG
    || x instanceof GOperator || x instanceof FOperator; */
  protected final EquivalenceClassFactory equivalenceClassFactory;
  protected final Collection<Optimisation> optimisations;
  protected final ProductControllerSynthesis product;
  protected final Map<UnaryModalOperator, Set<UnaryModalOperator>> topmostSlaves = new HashMap<>();
  protected final ValuationSetFactory valuationSetFactory;

  AccLocalControllerSynthesis(ProductControllerSynthesis product, Factories factories,
    Collection<Optimisation> opts) {
    this.product = product;
    this.valuationSetFactory = factories.vsFactory;
    this.equivalenceClassFactory = factories.eqFactory;
    this.optimisations = opts;

    for (UnaryModalOperator gOperator : getOverallFormula().accept(new SlaveSubFormulaVisitor())) {
      this.topmostSlaves.put(gOperator, gOperator.operand.accept(new TopMostOperatorVisitor()));
    }
  }

  protected void computeAccMasterForASingleGSet(Set<UnaryModalOperator> gSet,
    Map<Set<UnaryModalOperator>, TranSet<State>> result) {

    TranSet<State> avoidP = new TranSet<>(valuationSetFactory);

    for (State ps : product.getStates()) {
      // computeNonAccMasterTransForStateIgoringRankings
      Map<UnaryModalOperator, Integer> ranking = new HashMap<>();
      gSet.forEach(g -> ranking.put(g, -1));
      avoidP.addAll(computeNonAccMasterTransForState(ranking, ps));
    }

    if (!product.containsAllTransitions(avoidP)) {
      result.put(ImmutableSet.copyOf(gSet), avoidP);
    }
  }

  final Map<Set<UnaryModalOperator>, TranSet<State>>
  computeAccMasterOptions() {
    Set<Set<UnaryModalOperator>> gSets;
    EquivalenceClass clazz = equivalenceClassFactory.of(getOverallFormula());

    /*
    if (optimisations.contains(Optimisation.SKELETON)) {
      EquivalenceClass skeleton = clazz.exists(INFINITY_OPERATORS.negate());

      gSets = new HashSet<>();

      for (Set<Formula> sat : skeleton..sat(skeleton.getSupport())) {
        Set<UnaryModalOperator> gSet = new HashSet<>();
        sat.forEach(x -> gSet.add((UnaryModalOperator) x));
        gSets.add(gSet);
      }
    } else { */
    SlaveSubFormulaVisitor visitor = new SlaveSubFormulaVisitor();
    Set<UnaryModalOperator> gSetSupport = clazz.modalOperators().stream()
      .map(formula -> formula.accept(visitor))
      .reduce(new HashSet<>(), Sets::union);
    gSets = Sets.powerSet(gSetSupport);
    // }

    Map<Set<UnaryModalOperator>, TranSet<State>> result = new HashMap<>();
    for (Set<UnaryModalOperator> gSet : gSets) {
      computeAccMasterForASingleGSet(gSet, result);
    }
    return result;
  }

  private Map<Set<UnaryModalOperator>, Map<TranSet<State>, Integer>> computeAccSlavesOptions(
    UnaryModalOperator g) {
    Map<Set<UnaryModalOperator>, Map<TranSet<State>, Integer>> result = new HashMap<>();
    Set<Set<UnaryModalOperator>> gSets = Sets.powerSet(topmostSlaves.get(g));

    for (Set<UnaryModalOperator> gSet : gSets) {
      result.put(gSet, getSingleSlaveAccCond(g, gSet));
    }

    return result;
  }

  protected final TranSet<State> computeNonAccMasterTransForState(
    Map<UnaryModalOperator, Integer> ranking, State ps) {
    TranSet<State> result = new TranSet<>(valuationSetFactory);

    if (optimisations.contains(Optimisation.EAGER)) {
      BitSet sensitiveAlphabet = ps.getSensitiveAlphabet();

      for (BitSet valuation : BitSets.powerSet(sensitiveAlphabet)) {
        if (!slavesEntail(ps, ranking, valuation, ps.primaryState.getEquivalenceClass())) {
          result.addAll(ps, valuationSetFactory.of(valuation, sensitiveAlphabet));
        }
      }
    } else {
      if (!slavesEntail(ps, ranking, null, ps.primaryState.getEquivalenceClass())) {
        result.addAll(ps, valuationSetFactory.universe());
      }
    }

    return result;
  }

  final Map<UnaryModalOperator, Map<Set<UnaryModalOperator>,
    Map<TranSet<State>, Integer>>> getAllSlaveAcceptanceConditions() {
    Map<UnaryModalOperator, Map<Set<UnaryModalOperator>, Map<TranSet<State>, Integer>>> result =
      new HashMap<>();
    for (UnaryModalOperator g : product.secondaryAutomata.keySet()) {
      result.put(g, computeAccSlavesOptions(g));
    }

    return result;
  }

  protected final Formula getOverallFormula() {
    return product.primaryAutomaton.getInitialState().getEquivalenceClass().representative();
  }

  protected Map<TranSet<State>, Integer> getSingleSlaveAccCond(UnaryModalOperator g,
    Set<UnaryModalOperator> finalStates) {
    if (g instanceof FrequencyG) {
      return product.getControllerAcceptanceFrequencyG((FrequencyG) g, finalStates);
    }
    if (g instanceof GOperator) {
      Map<TranSet<State>, Integer> result = new HashMap<>();
      result.put(product.getControllerAcceptanceG((GOperator) g, finalStates), 0);
      return result;
    }
    if (g instanceof FOperator) {
      Map<TranSet<State>, Integer> result = new HashMap<>();
      result.put(product.getControllerAcceptanceF((FOperator) g, finalStates), 0);
      return result;
    }
    throw new IllegalArgumentException("Formula is not a valid label of slave automata.");
  }

  private boolean slavesEntail(State ps, Map<UnaryModalOperator, Integer> ranking,
    @Nullable BitSet valuation, EquivalenceClass consequent) {
    // FIXME This does not work for MDP synthesis, as we might have F operators here, which need
    // disjunctions
    EquivalenceClass conjunction = equivalenceClassFactory.getTrue();

    for (Map.Entry<UnaryModalOperator, Integer> entry : ranking.entrySet()) {
      UnaryModalOperator G = entry.getKey();
      int rank = entry.getValue();

      conjunction = conjunction.and(equivalenceClassFactory.of(G));

      if (optimisations.contains(Optimisation.EAGER)) {
        conjunction = conjunction.and(equivalenceClassFactory.of(G.operand));
      }
      FrequencySelfProductSlave.State rs = ps.getSecondaryState(G);
      if (rs != null) {
        if (rs.getOuter().mojmir.getLabel() instanceof FOperator) {
          continue;
        }
        for (Map.Entry<FrequencyMojmirSlaveAutomaton.MojmirState, Integer> stateEntry :
          rs.entrySet()) {
          if (stateEntry.getValue() >= rank) {
            if (optimisations.contains(Optimisation.EAGER)) {
              assert valuation != null;
              EquivalenceClass equivalenceClass = stateEntry.getKey().getEquivalenceClass();
              conjunction = conjunction.and(equivalenceClass.temporalStep(valuation));
            }
            conjunction = conjunction.and(stateEntry.getKey().getEquivalenceClass());
          }
        }
      }
    }

    EquivalenceClass testedConsequent;
    if (optimisations.contains(Optimisation.EAGER)) {
      assert valuation != null;
      testedConsequent = consequent.temporalStep(valuation);
    } else {
      testedConsequent = consequent;
    }

    EquivalenceClass antecedent = conjunction.substitute(formula -> {
      if ((formula instanceof GOperator || formula instanceof FOperator)
        && !ranking.containsKey(formula)) {
        return BooleanConstant.FALSE;
      }
      return formula;
    });

    return antecedent.implies(testedConsequent);
  }
}
