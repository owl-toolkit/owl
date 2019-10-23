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

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;

public final class OmegaAcceptanceCast {

  private static final Map<Class<? extends OmegaAcceptance>,
    Function<AllAcceptance, OmegaAcceptance>> ALL_CAST_MAP;
  private static final Map<Class<? extends OmegaAcceptance>,
    BiConsumer<AllAcceptance, BitSet>> ALL_EDGE_CAST_MAP;

  private static final Map<Class<? extends OmegaAcceptance>,
    Function<BuchiAcceptance, OmegaAcceptance>> BUCHI_CAST_MAP;
  private static final Map<Class<? extends OmegaAcceptance>,
    BiConsumer<BuchiAcceptance, BitSet>> BUCHI_EDGE_CAST_MAP;

  private static final Map<Class<? extends OmegaAcceptance>,
    Function<CoBuchiAcceptance, OmegaAcceptance>> CO_BUCHI_CAST_MAP;
  private static final Map<Class<? extends OmegaAcceptance>,
    BiConsumer<CoBuchiAcceptance, BitSet>> CO_BUCHI_EDGE_CAST_MAP;

  private static final Map<Class<? extends OmegaAcceptance>,
    Function<GeneralizedBuchiAcceptance, OmegaAcceptance>> GENERALIZED_BUCHI_CAST_MAP;
  private static final Map<Class<? extends OmegaAcceptance>,
    BiConsumer<GeneralizedBuchiAcceptance, BitSet>> GENERALIZED_BUCHI_EDGE_CAST_MAP;

  private static final Map<Class<? extends OmegaAcceptance>,
    Function<GeneralizedCoBuchiAcceptance, OmegaAcceptance>> GENERALIZED_CO_BUCHI_CAST_MAP;
  private static final Map<Class<? extends OmegaAcceptance>,
    BiConsumer<GeneralizedCoBuchiAcceptance, BitSet>> GENERALIZED_CO_BUCHI_EDGE_CAST_MAP;

  static {
    ALL_CAST_MAP = Map.of(
      BuchiAcceptance.class, x -> BuchiAcceptance.INSTANCE,
      CoBuchiAcceptance.class, x -> CoBuchiAcceptance.INSTANCE,
      GeneralizedBuchiAcceptance.class, x -> GeneralizedBuchiAcceptance.of(0),
      GeneralizedRabinAcceptance.class, x -> GeneralizedRabinAcceptance.of(
        GeneralizedRabinAcceptance.RabinPair.ofGeneralized(0, 0)),
      ParityAcceptance.class, x -> new ParityAcceptance(2, ParityAcceptance.Parity.MIN_EVEN),
      RabinAcceptance.class, x -> RabinAcceptance.of(1)
    );

    ALL_EDGE_CAST_MAP = Map.of(
      BuchiAcceptance.class, (x, y) -> y.set(0),
      ParityAcceptance.class, (x, y) -> y.set(0),
      RabinAcceptance.class, (x, y) -> y.set(1)
    );

    BUCHI_CAST_MAP = Map.of(
      GeneralizedRabinAcceptance.class, x -> RabinAcceptance.of(1),
      ParityAcceptance.class, x -> new ParityAcceptance(2, ParityAcceptance.Parity.MIN_EVEN),
      RabinAcceptance.class, x -> RabinAcceptance.of(1)
    );

    BUCHI_EDGE_CAST_MAP = Map.of(
      GeneralizedRabinAcceptance.class, (x, y) -> OmegaAcceptanceCast.bitSetShift(y),
      ParityAcceptance.class, (x, bitSet) -> {
        if (!bitSet.get(0)) {
          bitSet.set(1);
        }
      },
      RabinAcceptance.class, (x, y) -> OmegaAcceptanceCast.bitSetShift(y)
    );

    CO_BUCHI_CAST_MAP = Map.of(
      GeneralizedRabinAcceptance.class, x -> GeneralizedRabinAcceptance.of(x.booleanExpression()),
      ParityAcceptance.class, x -> new ParityAcceptance(2, ParityAcceptance.Parity.MIN_ODD),
      RabinAcceptance.class, x -> RabinAcceptance.of(1)
    );

    CO_BUCHI_EDGE_CAST_MAP = Map.of(
      ParityAcceptance.class, (x, bitSet) -> {
        if (!bitSet.get(0)) {
          bitSet.set(1);
        }
      },
      RabinAcceptance.class, (x, bitSet) -> {
        if (!bitSet.get(0)) {
          bitSet.set(1);
        }
      }
    );

    GENERALIZED_BUCHI_CAST_MAP = Map.of(
      GeneralizedRabinAcceptance.class, x -> GeneralizedRabinAcceptance.of(
        GeneralizedRabinAcceptance.RabinPair.ofGeneralized(0, x.acceptanceSets()))
    );

    GENERALIZED_BUCHI_EDGE_CAST_MAP = Map.of(
      GeneralizedRabinAcceptance.class, (x, y) -> OmegaAcceptanceCast.bitSetShift(y)
    );

    GENERALIZED_CO_BUCHI_CAST_MAP = Map.of(
      RabinAcceptance.class, x -> RabinAcceptance.of(x.size),
      GeneralizedRabinAcceptance.class, x -> GeneralizedRabinAcceptance.of(
        IntStream.range(0, x.size)
          .mapToObj(i -> GeneralizedRabinAcceptance.RabinPair.ofGeneralized(i, 0))
          .collect(Collectors.toList())
        )
    );

    GENERALIZED_CO_BUCHI_EDGE_CAST_MAP = Map.of(
      RabinAcceptance.class, (x, y) -> OmegaAcceptanceCast.bitSetSpread(x.size, y)
    );
  }

  private OmegaAcceptanceCast() {}

  public static <S, A extends OmegaAcceptance, B extends OmegaAcceptance> Automaton<S, B>
    cast(Automaton<S, A> automaton, Class<B> acceptanceClass) {
    @SuppressWarnings("unchecked")
    var oldAcceptanceClass = (Class<A>) automaton.acceptance().getClass();

    if (acceptanceClass.isAssignableFrom(oldAcceptanceClass)) {
      @SuppressWarnings("unchecked")
      var castedAutomaton = (Automaton<S, B>) automaton;
      return castedAutomaton;
    }

    if (acceptanceClass.equals(EmersonLeiAcceptance.class)) {
      return new CastedAutomaton<>(
        automaton, acceptanceClass.cast(new EmersonLeiAcceptance(automaton.acceptance())), null);
    }

    var cast = castMap(oldAcceptanceClass).get(acceptanceClass);
    var edgeCast = edgeCastMap(oldAcceptanceClass).get(acceptanceClass);

    if (cast == null) {
      throw new ClassCastException(String.format("Cannot cast %s into %s.",
        automaton.acceptance().getClass().getSimpleName(), acceptanceClass.getSimpleName()));
    }

    return new CastedAutomaton<>(automaton,
      acceptanceClass.cast(cast.apply(automaton.acceptance())),
      edgeCast == null ? null : edge -> {
        BitSet set = edge.acceptanceSets();
        edgeCast.accept(automaton.acceptance(), set);
        return edge.withAcceptance(set);
      });
  }

  public static boolean isInstanceOf(
    Class<? extends OmegaAcceptance> clazz1,
    Class<? extends OmegaAcceptance> clazz2) {
    return clazz2.isAssignableFrom(clazz1)
      || clazz2.equals(EmersonLeiAcceptance.class)
      || castMap(clazz1).containsKey(clazz2);
  }

  private static void bitSetShift(BitSet bitSet) {
    for (int i = bitSet.length(); i > 0; i--) {
      bitSet.set(i, bitSet.get(i - 1));
    }

    bitSet.clear(0);
  }

  private static void bitSetSpread(int size, BitSet bitSet) {
    for (int i = size - 1; i >= 0; i--) {
      boolean fin = bitSet.get(i);
      bitSet.set(2 * i, fin);
      bitSet.set(2 * i + 1, !fin);
    }
  }

  @SuppressWarnings("unchecked")
  private static <A extends OmegaAcceptance> Map<Class<? extends OmegaAcceptance>,
    Function<A, OmegaAcceptance>> castMap(Class<A> clazz) {
    if (AllAcceptance.class.equals(clazz)) {
      return (Map) ALL_CAST_MAP;
    }

    if (BuchiAcceptance.class.equals(clazz)) {
      return (Map) BUCHI_CAST_MAP;
    }

    if (CoBuchiAcceptance.class.equals(clazz)) {
      return (Map) CO_BUCHI_CAST_MAP;
    }

    if (GeneralizedBuchiAcceptance.class.equals(clazz)) {
      return (Map) GENERALIZED_BUCHI_CAST_MAP;
    }

    if (GeneralizedCoBuchiAcceptance.class.equals(clazz)) {
      return (Map) GENERALIZED_CO_BUCHI_CAST_MAP;
    }

    return Map.of();
  }

  @SuppressWarnings("unchecked")
  private static <A extends OmegaAcceptance> Map<Class<? extends OmegaAcceptance>,
    BiConsumer<A, BitSet>> edgeCastMap(Class<A> clazz) {
    if (AllAcceptance.class.equals(clazz)) {
      return (Map) ALL_EDGE_CAST_MAP;
    }

    if (BuchiAcceptance.class.equals(clazz)) {
      return (Map) BUCHI_EDGE_CAST_MAP;
    }

    if (CoBuchiAcceptance.class.equals(clazz)) {
      return (Map) CO_BUCHI_EDGE_CAST_MAP;
    }

    if (GeneralizedBuchiAcceptance.class.equals(clazz)) {
      return (Map) GENERALIZED_BUCHI_EDGE_CAST_MAP;
    }

    if (GeneralizedCoBuchiAcceptance.class.equals(clazz)) {
      return (Map) GENERALIZED_CO_BUCHI_EDGE_CAST_MAP;
    }

    return Map.of();
  }

  private static class CastedAutomaton<S, A extends OmegaAcceptance, B extends OmegaAcceptance>
    implements Automaton<S, A> {

    private final A acceptance;
    private final Automaton<S, B> backingAutomaton;

    @Nullable
    private final Function<Edge<S>, Edge<S>> edgeCast;

    private CastedAutomaton(
      Automaton<S, B> backingAutomaton,
      A acceptance,
      @Nullable Function<Edge<S>, Edge<S>> edgeCast) {
      this.acceptance = acceptance;
      this.backingAutomaton = backingAutomaton;
      this.edgeCast = edgeCast;
    }

    @Override
    public A acceptance() {
      return acceptance;
    }

    @Override
    public ValuationSetFactory factory() {
      return backingAutomaton.factory();
    }

    @Override
    public Set<S> initialStates() {
      return backingAutomaton.initialStates();
    }

    @Override
    public Set<S> states() {
      return backingAutomaton.states();
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      var edges = backingAutomaton.edges(state, valuation);

      return edgeCast == null
        ? edges
        : Collections3.transformSet(edges, edgeCast);
    }

    @Override
    public Set<Edge<S>> edges(S state) {
      var edges = backingAutomaton.edges(state);

      return edgeCast == null
        ? edges
        : Collections3.transformSet(edges, edgeCast);
    }

    @Override
    public Map<Edge<S>, ValuationSet> edgeMap(S state) {
      var edgeMap = backingAutomaton.edgeMap(state);

      return edgeCast == null
        ? edgeMap
        : Collections3.transformMap(edgeMap, edgeCast);
    }

    @Override
    public ValuationTree<Edge<S>> edgeTree(S state) {
      var edgeTree = backingAutomaton.edgeTree(state);

      return edgeCast == null
        ? edgeTree
        : edgeTree.map(x -> Collections3.transformSet(x, edgeCast));
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return backingAutomaton.preferredEdgeAccess();
    }
  }
}
