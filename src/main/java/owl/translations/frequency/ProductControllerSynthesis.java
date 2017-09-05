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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaPrinter;
import owl.collections.ValuationSet;
import owl.factories.Factories;
import owl.ltl.FOperator;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;

public class ProductControllerSynthesis extends
  Automaton<ProductControllerSynthesis.State,
    GeneralizedRabinAcceptance2<ProductControllerSynthesis.State>> {

  protected final Map<UnaryModalOperator, FrequencySelfProductSlave> secondaryAutomata;
  final MasterAutomaton primaryAutomaton;

  public ProductControllerSynthesis(MasterAutomaton primaryAutomaton,
    Map<UnaryModalOperator, FrequencySelfProductSlave> slaves, Factories factories) {
    super(new GeneralizedRabinAcceptance2<>(ImmutableList.of()), factories);
    // relevant secondaryAutomata dynamically
    // computed from primaryAutomaton formula
    // master formula
    this.primaryAutomaton = primaryAutomaton;
    this.secondaryAutomata = slaves;

    setInitialState(new State(primaryAutomaton.getInitialState(),
      secondaryAutomata.keySet(), k -> secondaryAutomata.get(k).getInitialState()));
  }

  boolean containsAllTransitions(TranSet<State> trans) {
    return transitions.entrySet().stream().allMatch(entry -> entry.getValue().entrySet().stream()
      .allMatch(succ -> trans.containsAll(entry.getKey(), succ.getValue())));
  }

  public TranSet<State> getControllerAcceptanceF(FOperator f,
    Set<UnaryModalOperator> finalStates) {
    return getSucceedingProductTransitions(secondaryAutomata.get(f), -1, finalStates);
  }

  public Map<TranSet<State>, Integer> getControllerAcceptanceFrequencyG(FrequencyG g,
    Set<UnaryModalOperator> finalStates) {
    Map<TranSet<State>, Integer> result = new HashMap<>();
    FrequencySelfProductSlave slave = secondaryAutomata.get(g);
    int maxTokenNumber = slave.mojmir.size();
    for (int i = 0; i <= maxTokenNumber; i++) {
      TranSet<State> relevantTransitions = getSucceedingProductTransitions(slave, i, finalStates);
      if (!relevantTransitions.isEmpty()) {
        result.put(relevantTransitions, i);
      }
    }
    return result;
  }

  public TranSet<State> getControllerAcceptanceG(GOperator g,
    Set<UnaryModalOperator> finalStates) {
    TranSet<State> failP = new TranSet<>(vsFactory);
    FrequencySelfProductSlave slave = secondaryAutomata.get(g);

    for (State ps : getStates()) {
      TranSet<State> fail = new TranSet<>(vsFactory);
      FrequencySelfProductSlave.State rs = ps.secondaryStates.get(slave.mojmir.getLabel());

      if (rs != null) { // relevant slave
        for (FrequencyMojmirSlaveAutomaton.MojmirState fs : rs.keySet()) {
          fail.addAll(ps, slave.mojmir.getFailingMojmirTransitions(fs, finalStates));
        }
      }
      failP.addAll(fail);
    }

    return failP;
  }

  /**
   * Returns succeeding product transitions.
   *
   * @param rank
   *     rank is either semantically a rank or the token-number of the states rank=-1 means that the
   *     rank does not matter (used for ProductControllerSynthesis, F-slave).
   **/
  private TranSet<State> getSucceedingProductTransitions(
    FrequencySelfProductSlave slave, int rank, Set<UnaryModalOperator> finalStates) {
    TranSet<State> succeedP = new TranSet<>(vsFactory);
    for (State ps : getStates()) {
      succeedP.addAll(ps, ps.getSucceedTransitions(slave.mojmir, rank, finalStates));
    }
    return succeedP;
  }

  /**
   * This method is important, because currently the acceptance is computed after the product is
   * constructed.
   */
  protected void setAcceptance(GeneralizedRabinAcceptance2<State> acc) {
    this.acceptance = acc;
  }

  @Override
  public void toHoa(HOAConsumer ho, EnumSet<HoaPrinter.HoaOption> options) {
    HoaConsumerExtended hoa = new HoaConsumerGeneralisedRabin<>(ho, vsFactory,
      variables, getInitialStates(), acceptance, size());
    toHoaBody(hoa);
    hoa.notifyEnd();
  }

  @Override
  protected void toHoaBodyEdge(State state, HoaConsumerExtended hoa) {
    this.getSuccessors(state).forEach((k, v) -> hoa.addEdge(v, k.successor()));
  }

  public class State implements AutomatonState<State> {
    final MasterAutomaton.MasterState primaryState;
    final ImmutableMap<UnaryModalOperator, FrequencySelfProductSlave.State> secondaryStates;
    private final int hashCode;

    State(MasterAutomaton.MasterState primaryState, Collection<UnaryModalOperator> keys,
      Function<UnaryModalOperator, FrequencySelfProductSlave.State> constructor) {
      this.primaryState = primaryState;

      ImmutableMap.Builder<UnaryModalOperator, FrequencySelfProductSlave.State> builder =
        ImmutableMap.builder();
      keys.forEach(k -> builder.put(k, constructor.apply(k)));
      this.secondaryStates = builder.build();

      hashCode = Objects.hash(primaryState, secondaryStates);
    }

    State(MasterAutomaton.MasterState primaryState,
      ImmutableMap<UnaryModalOperator, FrequencySelfProductSlave.State> secondaryStates) {
      this.primaryState = primaryState;
      this.secondaryStates = secondaryStates;

      hashCode = Objects.hash(primaryState, secondaryStates);
    }

    protected State constructState(MasterAutomaton.MasterState primaryState,
      ImmutableMap<UnaryModalOperator, FrequencySelfProductSlave.State> secondaryStates) {
      return new State(primaryState, secondaryStates);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof State)) {
        return false;
      }
      State state = (State) o;
      return hashCode == state.hashCode
        && Objects.equals(primaryState, state.primaryState)
        && Objects.equals(secondaryStates, state.secondaryStates);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Nullable
    FrequencySelfProductSlave.State getSecondaryState(UnaryModalOperator key) {
      return this.secondaryStates.get(key);
    }

    @Override
    @Nonnull
    public BitSet getSensitiveAlphabet() {
      BitSet sensitiveLetters = primaryState.getSensitiveAlphabet();

      for (FrequencySelfProductSlave.State secondaryState : secondaryStates.values()) {
        sensitiveLetters.or(secondaryState.getSensitiveAlphabet());
      }

      return sensitiveLetters;
    }

    public ValuationSet getSucceedTransitions(FrequencyMojmirSlaveAutomaton mojmir, int rank,
      Set<UnaryModalOperator> finalStates) {
      ValuationSet succeed = vsFactory.empty();
      FrequencySelfProductSlave.State rs = secondaryStates.get(mojmir.getLabel());
      if (rs != null) { // relevant slave
        if (rank == -1) {
          for (Map.Entry<FrequencyMojmirSlaveAutomaton.MojmirState, Integer> stateIntegerEntry :
            rs.entrySet()) {
            succeed = vsFactory.union(succeed,
              mojmir.getSucceedMojmirTransitions(stateIntegerEntry.getKey(), finalStates));
          }
          return succeed;
        } else {
          Map<FrequencyMojmirSlaveAutomaton.MojmirState, ValuationSet> succeedingTransitions =
            new HashMap<>();

          for (FrequencyMojmirSlaveAutomaton.MojmirState state : rs.keySet()) {
            ValuationSet succeedingForState =
              mojmir.getSucceedMojmirTransitions(state, finalStates);
            if (!succeedingForState.isEmpty()) {
              succeedingTransitions.put(state, succeedingForState);
            }
          }

          for (Set<FrequencyMojmirSlaveAutomaton.MojmirState> stateSet : Sets
            .powerSet(succeedingTransitions.keySet())) {
            if (stateSet.stream().mapToInt(rs::get).sum() == rank) {
              ValuationSet successor = vsFactory.universe();
              for (FrequencyMojmirSlaveAutomaton.MojmirState state : stateSet) {
                successor = vsFactory.intersection(successor, succeedingTransitions.get(state));
              }
              removeAllTransitionsCoveredByALargerRank(rank, rs, succeedingTransitions, successor);
              succeed = vsFactory.union(succeed, successor);
            }
          }
        }
      }

      return succeed;
    }

    @Override
    @Nullable
    public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
      Edge<MasterAutomaton.MasterState> primarySuccessorEdge = primaryState.getSuccessor(valuation);

      if (primarySuccessorEdge == null) {
        return null;
      }

      ImmutableMap.Builder<UnaryModalOperator, FrequencySelfProductSlave.State> builder =
        ImmutableMap.builder();

      for (UnaryModalOperator key : secondaryAutomata.keySet()) {
        FrequencySelfProductSlave.State secondary = secondaryStates.get(key);

        if (secondary != null) {
          Edge<FrequencySelfProductSlave.State> edge = secondary.getSuccessor(valuation);

          if (edge == null) {
            return null;
          } else {
            builder.put(key, edge.successor());
          }
        }
      }

      MasterAutomaton.MasterState primarySuccessorState = primarySuccessorEdge.successor();
      return Edge.of(constructState(primarySuccessorState, builder.build()));
    }

    private void removeAllTransitionsCoveredByALargerRank(int rank,
      FrequencySelfProductSlave.State rs,
      Map<FrequencyMojmirSlaveAutomaton.MojmirState, ValuationSet> succeedingTransitions,
      ValuationSet successor) {
      ValuationSet valuationSet = successor;
      for (Set<FrequencyMojmirSlaveAutomaton.MojmirState> set :
        Sets.powerSet(succeedingTransitions.keySet())) {
        if (set.stream().mapToInt(rs::get).sum() > rank) {
          for (FrequencyMojmirSlaveAutomaton.MojmirState state : set) {
            valuationSet = vsFactory.intersection(valuationSet,
              vsFactory.complement(succeedingTransitions.get(state)));
          }
        }
      }
    }

    @Override
    public String toString() {
      return "(" + primaryState + "::" + secondaryStates + ')';
    }
  }
}
