/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.collections.Collections3;

/**
 * This class provides functionality to cast an automaton to an automaton with a more generic
 * acceptance condition. This operation yields a on-the-fly generated view on the backing automaton.
 * It is assumed that the backing automaton is not modified anymore after the cast.
 */
public final class OmegaAcceptanceCast {

  private static final Map<Class<? extends EmersonLeiAcceptance>,
    Function<AllAcceptance, EmersonLeiAcceptance>> ALL_CAST_MAP;
  private static final Map<Class<? extends EmersonLeiAcceptance>,
    BiConsumer<AllAcceptance, BitSet>> ALL_EDGE_CAST_MAP;

  private static final Map<Class<? extends EmersonLeiAcceptance>,
    Function<BuchiAcceptance, EmersonLeiAcceptance>> BUCHI_CAST_MAP;
  private static final Map<Class<? extends EmersonLeiAcceptance>,
    BiConsumer<BuchiAcceptance, BitSet>> BUCHI_EDGE_CAST_MAP;

  private static final Map<Class<? extends EmersonLeiAcceptance>,
    Function<CoBuchiAcceptance, EmersonLeiAcceptance>> CO_BUCHI_CAST_MAP;
  private static final Map<Class<? extends EmersonLeiAcceptance>,
    BiConsumer<CoBuchiAcceptance, BitSet>> CO_BUCHI_EDGE_CAST_MAP;

  private static final Map<Class<? extends EmersonLeiAcceptance>,
    Function<GeneralizedBuchiAcceptance, EmersonLeiAcceptance>> GENERALIZED_BUCHI_CAST_MAP;
  private static final Map<Class<? extends EmersonLeiAcceptance>,
    BiConsumer<GeneralizedBuchiAcceptance, BitSet>> GENERALIZED_BUCHI_EDGE_CAST_MAP;

  private static final Map<Class<? extends EmersonLeiAcceptance>,
    Function<GeneralizedCoBuchiAcceptance, EmersonLeiAcceptance>> GENERALIZED_CO_BUCHI_CAST_MAP;
  private static final Map<Class<? extends EmersonLeiAcceptance>,
    BiConsumer<GeneralizedCoBuchiAcceptance, BitSet>> GENERALIZED_CO_BUCHI_EDGE_CAST_MAP;

  static {
    ALL_CAST_MAP = Map.of(
      BuchiAcceptance.class, x -> BuchiAcceptance.INSTANCE,
      CoBuchiAcceptance.class, x -> CoBuchiAcceptance.INSTANCE,
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
      GeneralizedRabinAcceptance.class,
      x -> GeneralizedRabinAcceptance.ofPartial(x.booleanExpression()).orElseThrow(),
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
      RabinAcceptance.class, x -> RabinAcceptance.of(x.acceptanceSets()),
      GeneralizedRabinAcceptance.class, x -> GeneralizedRabinAcceptance.of(
        IntStream.range(0, x.acceptanceSets())
          .mapToObj(i -> GeneralizedRabinAcceptance.RabinPair.ofGeneralized(i, 0))
          .toList()
        )
    );

    GENERALIZED_CO_BUCHI_EDGE_CAST_MAP = Map.of(
      RabinAcceptance.class, (x, y) -> OmegaAcceptanceCast.bitSetSpread(x.acceptanceSets(), y)
    );
  }

  private OmegaAcceptanceCast() {}

  /**
   * Cast the given automaton to the given acceptance condition if possible. A conversion is
   * considered possible if (trivial) rewriting of the acceptance condition is done and no
   * changes to state space are necessary, e.g. a Büchi condition can be translated to a Rabin
   * condition, but a Rabin condition (even only with a single pair) cannot be cast to parity
   * acceptance condition.
   *
   * @param automaton The automaton. It is assumed that after calling this method the automaton is
   *     not modified anymore.
   * @param acceptanceClass The desired acceptance condition.
   * @param <S> The state type.
   * @param <A> The current acceptance type.
   * @param <B> The desired acceptance type.
   * @return A view on the given automaton with the necessary changes.
   */
  public static <S, A extends EmersonLeiAcceptance, B extends EmersonLeiAcceptance>
    Automaton<S, ? extends B>
    cast(Automaton<S, A> automaton, Class<? extends B> acceptanceClass) {

    A oldAcceptance = automaton.acceptance();

    if (acceptanceClass.isAssignableFrom(oldAcceptance.getClass())) {
      @SuppressWarnings("unchecked")
      var castedAutomaton = (Automaton<S, ? extends B>) automaton;
      return castedAutomaton;
    }

    assert !acceptanceClass.equals(EmersonLeiAcceptance.class);

    @SuppressWarnings("unchecked")
    var oldAcceptanceClass = (Class<A>) oldAcceptance.getClass();
    var edgeCast = edgeCastMap(oldAcceptanceClass).get(acceptanceClass);
    return new CastedAutomaton<>(automaton,
      cast(oldAcceptance, oldAcceptanceClass, acceptanceClass),
      edgeCast == null ? null : edge -> {
        BitSet set = edge.colours().copyInto(new BitSet());
        edgeCast.accept(oldAcceptance, set);
        return edge.withAcceptance(set);
      });
  }

  public static boolean isInstanceOf(
    Class<? extends EmersonLeiAcceptance> clazz1,
    Class<? extends EmersonLeiAcceptance> clazz2) {
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

  private static <A extends EmersonLeiAcceptance, B extends EmersonLeiAcceptance>
    B cast(A acceptance, Class<A> oldClass, Class<B> newClass) {
    Function<A, EmersonLeiAcceptance> cast = castMap(oldClass).get(newClass);

    if (cast == null) {
      throw new ClassCastException(String.format("Cannot cast %s (%s) to %s.",
        oldClass.getSimpleName(), acceptance.booleanExpression(), newClass.getSimpleName()));
    }

    return newClass.cast(cast.apply(acceptance));
  }

  @SuppressWarnings("unchecked")
  private static <A extends EmersonLeiAcceptance> Map<Class<? extends EmersonLeiAcceptance>,
    Function<A, EmersonLeiAcceptance>> castMap(Class<A> clazz) {
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
  private static <A extends EmersonLeiAcceptance> Map<Class<? extends EmersonLeiAcceptance>,
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

  private static class CastedAutomaton<S, A extends EmersonLeiAcceptance>
    implements Automaton<S, A> {

    private final A acceptance;
    private final Automaton<S, ?> backingAutomaton;

    @Nullable
    private final Function<Edge<S>, Edge<S>> edgeCast;

    private CastedAutomaton(
      Automaton<S, ?> backingAutomaton,
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
    public List<String> atomicPropositions() {
      return backingAutomaton.atomicPropositions();
    }

    @Override
    public BddSetFactory factory() {
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
    public Set<Edge<S>> edges(S state) {
      var edges = backingAutomaton.edges(state);

      return edgeCast == null
        ? edges
        : Collections3.transformSet(edges, edgeCast);
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      var edges = backingAutomaton.edges(state, valuation);

      return edgeCast == null
        ? edges
        : Collections3.transformSet(edges, edgeCast);
    }

    @Override
    public Map<Edge<S>, BddSet> edgeMap(S state) {
      var edgeMap = backingAutomaton.edgeMap(state);

      return edgeCast == null
        ? edgeMap
        : Collections3.transformMap(edgeMap, edgeCast);
    }

    @Override
    public MtBdd<Edge<S>> edgeTree(S state) {
      var edgeTree = backingAutomaton.edgeTree(state);

      return edgeCast == null
        ? edgeTree
        : edgeTree.map(x -> Collections3.transformSet(x, edgeCast));
    }
  }
}
