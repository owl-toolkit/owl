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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.UnaryModalOperator;
import owl.ltl.visitors.Visitor;

final class FrequencyMojmirSlaveAutomaton extends
  Automaton<FrequencyMojmirSlaveAutomaton.MojmirState, AllAcceptance> {

  private static final Visitor<Formula> visitor = new FrequencySlaveUnfoldVisitor();
  private static final Function<Formula, Formula>
    unfoldVisitor = formula -> formula.accept(visitor);
  private final EquivalenceClassFactory factory;
  private final UnaryModalOperator label;
  public ImmutableSet<Formula> base;
  public ImmutableMap<Set<Formula>, Integer> indices;

  FrequencyMojmirSlaveAutomaton(UnaryModalOperator formula, Factories factories,
    EnumSet<Optimisation> optimisations) {
    super(AllAcceptance.INSTANCE, factories);
    factory = factories.eqFactory;
    EquivalenceClass clazz = factory.of(formula.operand);
    label = formula;
    this.base = ImmutableSet.copyOf(getAcceptanceRelevantFormulas(
      Collections.singleton(formula.operand)));

    ImmutableMap.Builder<Set<Formula>, Integer> builder = ImmutableMap.builder();
    int index = 0;

    for (Set<Formula> set : Sets.powerSet(this.base)) {
      builder.put(set, index);
      index++;
    }

    indices = builder.build();

    if (optimisations.contains(Optimisation.EAGER)) {
      setInitialState(new MojmirState(clazz.substitute(unfoldVisitor), true));
    } else {
      setInitialState(new MojmirState(clazz, false));
    }
  }

  private static Set<? extends Formula>
  getAcceptanceRelevantFormulas(Set<? extends Formula> support) {
    return support.stream().flatMap(x -> x.accept(new TopMostOperatorVisitor()).stream()).collect(
      ImmutableSet.toImmutableSet());
  }

  public ValuationSet getFailingMojmirTransitions(MojmirState state,
    Set<? extends Formula> acceptingFormulas) {
    AtomicReference<ValuationSet> fail = new AtomicReference<>(vsFactory.empty());
    int failIndex = 2 * indices.get(Sets.intersection(base, acceptingFormulas));

    getSuccessors(state).forEach((edge, valuation) -> {
      if (edge.inSet(failIndex)) {
        fail.updateAndGet(f -> vsFactory.union(f, valuation));
      }
    });

    return fail.get();
  }

  public UnaryModalOperator getLabel() {
    return label;
  }

  public Edge<MojmirState> getStateSuccessor(MojmirState state, @Nonnull BitSet valuation) {
    EquivalenceClass successor;

    if (state.eager) {
      successor = state.equivalenceClass.temporalStep(valuation).substitute(unfoldVisitor);
    } else {
      successor = state.equivalenceClass.substitute(unfoldVisitor).temporalStep(valuation);
    }

    // TODO: enable, if other code is fixed.
    // if (successor.isFalse()) {
    //  return null;
    // }

    MojmirState successorState = new MojmirState(successor, state.eager);
    BitSet acceptance = new BitSet();

    indices.forEach((set, index) -> {
      // Initial state is accepting. Thus all tranistions are accepting.
      if (this.isAccepting(getInitialState(), set)) {
        acceptance.set(2 * index + 1);
        return;
      }

      // We just moved from a non-accepting region to an accepting region.
      if (!isAccepting(state, set) && isAccepting(successorState, set)) {
        acceptance.set(2 * index + 1);
        return;
      }

      // Run gets trapped in a non-accepting sink.
      if (isSink(successorState) && !isAccepting(successorState, set) && !isAccepting(state, set)) {
        acceptance.set(2 * index);
      }
    });

    return Edge.of(successorState, acceptance);
  }

  public ValuationSet getSucceedMojmirTransitions(MojmirState state,
    Set<? extends Formula> acceptingFormulas) {
    AtomicReference<ValuationSet> succeed = new AtomicReference<>(vsFactory.empty());
    int succeedIndex = 2 * indices.get(Sets.intersection(base, acceptingFormulas)) + 1;

    getSuccessors(state).forEach((edge, valuation) -> {
      if (edge.inSet(succeedIndex)) {
        succeed.updateAndGet(f -> vsFactory.union(f, valuation));
      }
    });

    return succeed.get();
  }

  public boolean isAccepting(MojmirState state, Set<? extends Formula> environment) {
    EquivalenceClass clazz = factory.conjunction(environment.stream()
      .map(factory::of)
      .collect(Collectors.toList()));
    return clazz.implies(state.equivalenceClass);
  }

  public boolean isSink(MojmirState state) {
    BitSet sensitiveAlphabet = state.getSensitiveAlphabet();

    for (BitSet valuation : BitSets.powerSet(sensitiveAlphabet)) {
      EquivalenceClass successor = state.getEquivalenceClass().substitute(unfoldVisitor)
        .temporalStep(valuation);

      if (!successor.equals(state.getEquivalenceClass())) {
        return false;
      }
    }

    return true;
  }

  @Override
  protected void toHoaBodyEdge(MojmirState state, HoaConsumerExtended hoa) {
    // empty
  }

  public final class MojmirState extends EquivalenceClassState<MojmirState> {
    MojmirState(EquivalenceClass equivalenceClass, boolean eager) {
      super(equivalenceClass, eager);
    }

    @Override
    public Edge<MojmirState> getSuccessor(BitSet valuation) {
      return FrequencyMojmirSlaveAutomaton.this.getStateSuccessor(this, valuation);
    }
  }
}
