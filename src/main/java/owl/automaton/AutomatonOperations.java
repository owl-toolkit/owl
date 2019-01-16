/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.Automaton.Property.DETERMINISTIC;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PrimitiveIterator.OfInt;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;

public final class AutomatonOperations {

  private AutomatonOperations() {}

  private static ValuationSetFactory sharedAlphabet(Stream<ValuationSetFactory> factories) {
    Iterator<ValuationSetFactory> iterator = factories.iterator();
    ValuationSetFactory factory = iterator.next();

    while (iterator.hasNext()) {
      ValuationSetFactory otherFactory = iterator.next();

      if (Collections.indexOfSubList(otherFactory.alphabet(), factory.alphabet()) == 0) {
        factory = otherFactory;
      } else if (Collections.indexOfSubList(factory.alphabet(), otherFactory.alphabet()) != 0) {
        throw new IllegalArgumentException("Could not find shared alphabet.");
      }
    }

    return factory;
  }

  /**
   * Constructs an automaton recognizing the intersection of languages of the given automata.
   *
   * @param automata
   *     A list of automata over the same alphabet ({@link ValuationSetFactory}). The only supported
   *     acceptance is BuchiAcceptance.
   * @param <S>
   *     The type of the states. Can be set to Object.
   *
   * @return An automaton that is constructed on-the-fly.
   */
  public static <S> Automaton<List<S>, GeneralizedBuchiAcceptance> intersectionBuchi(
    List<Automaton<S, ? extends BuchiAcceptance>> automata) {
    checkArgument(!automata.isEmpty(), "No automaton was passed.");

    Set<List<S>> initialStates = new HashSet<>();

    for (List<S> initialState : Sets.cartesianProduct(
      automata.stream().map(Automaton::initialStates).collect(Collectors.toList()))) {
      initialStates.add(List.copyOf(initialState));
    }

    BiFunction<List<S>, BitSet, Set<Edge<List<S>>>> edgesFunction = (state, letter) -> {
      List<Set<Edge<S>>> edges = new ArrayList<>();

      for (int i = 0; i < state.size(); i++) {
        edges.add(automata.get(i).edges(state.get(i), letter));
      }

      Set<Edge<List<S>>> edgesComputed = new HashSet<>();

      for (List<Edge<S>> edge : Sets.cartesianProduct(edges)) {
        List<S> successors = new ArrayList<>();
        BitSet acceptance = new BitSet();

        for (int i = 0; i < edge.size(); i++) {
          Edge<S> x = edge.get(i);
          successors.add(x.successor());
          acceptance.set(i, x.hasAcceptanceSets());
        }

        edgesComputed.add(Edge.of(List.copyOf(successors), acceptance));
      }

      return edgesComputed;
    };

    ValuationSetFactory factory = sharedAlphabet(automata.stream().map(Automaton::factory));
    return new ImplicitNonDeterministicEdgesAutomaton<>(factory, initialStates,
      GeneralizedBuchiAcceptance.of(automata.size()), edgesFunction);
  }


  /**
   * Constructs an automaton recognizing the intersection of languages of the given automata.
   *
   * @param automata
   *     A list of automata over the same alphabet ({@link ValuationSetFactory}). The only supported
   *     acceptance are AllAcceptance, CoBuchiAcceptance and GeneralisedBuchiAcceptance. The given
   *     automata need to be deterministic.
   * @param <S>
   *     The type of the states. Can be set to Object.
   *
   * @return An automaton that is constructed on-the-fly.
   */
  public static <S> Automaton<List<S>, OmegaAcceptance> intersection(
    List<Automaton<S, ? extends OmegaAcceptance>> automata) {
    checkArgument(!automata.isEmpty(), "No automaton was passed.");

    ListAutomatonBuilder<S> builder = new ListAutomatonBuilder<>(false, true);
    int offset = 1;

    for (Automaton<S, ? extends OmegaAcceptance> automaton : automata) {
      checkArgument(automaton.is(DETERMINISTIC), "Only deterministic automata supported");

      if (automaton.acceptance() instanceof AllAcceptance) {
        builder.all.add(AutomatonUtil.cast(automaton, AllAcceptance.class));
      } else if (automaton.acceptance() instanceof CoBuchiAcceptance) {
        builder.coBuchi.add(AutomatonUtil.cast(automaton, CoBuchiAcceptance.class));
      } else if (automaton.acceptance() instanceof GeneralizedBuchiAcceptance) {
        builder.buchi.add(AutomatonUtil.cast(automaton, GeneralizedBuchiAcceptance.class));
        builder.acceptanceRemapping.add(offset);
        offset += automaton.acceptance().acceptanceSets();
      } else {
        throw new IllegalArgumentException(
          "Unsupported Acceptance Type " + automaton.acceptance());
      }
    }

    OmegaAcceptance acceptance;

    if (builder.buchi.isEmpty()) {
      acceptance = CoBuchiAcceptance.INSTANCE;
    } else if (builder.coBuchi.isEmpty()) {
      acceptance = GeneralizedBuchiAcceptance.of(offset - 1);
    } else if (offset < 2) {
      acceptance = RabinAcceptance.of(RabinPair.of(0));
    } else {
      acceptance = GeneralizedRabinAcceptance.of(RabinPair.ofGeneralized(0, offset - 1));
    }

    ValuationSetFactory factory = sharedAlphabet(automata.stream().map(Automaton::factory));
    return AutomatonFactory.create(factory, builder.init(), acceptance, builder::successor);
  }

  public static <S> Automaton<List<S>, BuchiAcceptance> union(
    List<Automaton<S, BuchiAcceptance>> automata) {
    checkArgument(!automata.isEmpty(), "No automaton was passed.");
    assert automata.stream().allMatch(x -> x.is(DETERMINISTIC));

    ListAutomatonBuilder<S> builder = new ListAutomatonBuilder<>(true, false);
    ValuationSetFactory factory = sharedAlphabet(automata.stream().map(Automaton::factory));
    builder.buchi.addAll(automata);

    return AutomatonFactory.create(factory, builder.init(), BuchiAcceptance.INSTANCE,
      builder::successor);
  }

  private static final class ListAutomatonBuilder<S> {
    final IntList acceptanceRemapping = new IntArrayList();
    final List<Automaton<S, AllAcceptance>> all = new ArrayList<>();
    final List<Automaton<S, ? extends GeneralizedBuchiAcceptance>> buchi = new ArrayList<>();
    final List<Automaton<S, CoBuchiAcceptance>> coBuchi = new ArrayList<>();
    final boolean collapseBuchi;
    final boolean fastNull;

    ListAutomatonBuilder(boolean collapseBuchi, boolean fastNull) {
      this.collapseBuchi = collapseBuchi;
      this.fastNull = fastNull;
    }

    Automaton<S, ?> getAutomaton(int i) {
      int index = i;
      if (index < all.size()) {
        return all.get(index);
      }

      index -= all.size();

      if (index < coBuchi.size()) {
        return coBuchi.get(index);
      }

      index -= coBuchi.size();
      return buchi.get(index);
    }

    int getOffset(int i) {
      int index = i;
      if (index < all.size()) {
        return -1;
      }

      index -= all.size();

      if (index < coBuchi.size()) {
        return 0;
      }

      index -= coBuchi.size();
      return acceptanceRemapping.getInt(index);
    }

    List<S> init() {
      return Stream.of(all, coBuchi, buchi)
        .flatMap(List::stream)
        .map(Automaton::onlyInitialState)
        .collect(Collectors.toUnmodifiableList());
    }

    @Nullable
    Edge<List<S>> successor(List<S> list, BitSet valuation) {
      List<S> successor = new ArrayList<>(list.size());
      BitSet acceptanceSets = new BitSet();

      for (int i = 0; i < list.size(); i++) {
        S state = list.get(i);

        if (state == null) {
          assert !fastNull;
          successor.add(null);
          continue;
        }

        Edge<S> edge = getAutomaton(i).edge(state, valuation);

        if (edge == null) {
          if (fastNull) {
            return null;
          }

          successor.add(null);
          continue;
        }

        successor.add(edge.successor());

        if (collapseBuchi) {
          assert all.isEmpty();
          assert coBuchi.isEmpty();
          assert edge.largestAcceptanceSet() <= 0;

          if (edge.hasAcceptanceSets()) {
            acceptanceSets.set(0);
          }
        } else {
          int offset = getOffset(i);

          if (offset > 0) {
            OfInt iterator = edge.acceptanceSetIterator();

            while (iterator.hasNext()) {
              acceptanceSets.set(iterator.nextInt() + offset);
            }
          } else if (offset == 0 && edge.hasAcceptanceSets()) {
            acceptanceSets.set(0);
          }
        }
      }

      return Edge.of(Collections.unmodifiableList(successor), acceptanceSets);
    }
  }
}
