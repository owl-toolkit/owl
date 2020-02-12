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

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;

/**
 * This class provides functionality to cast an automaton to an automaton with a more generic
 * acceptance condition. This operation yields a on-the-fly generated view on the backing automaton.
 * It is assumed that the backing automaton is not modified anymore after the cast.
 */
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
  public static <S, A extends OmegaAcceptance, B extends OmegaAcceptance> Automaton<S, B>
    cast(Automaton<S, A> automaton, Class<B> acceptanceClass) {
    A oldAcceptance = automaton.acceptance();

    @SuppressWarnings("unchecked")
    var oldAcceptanceClass = (Class<A>) oldAcceptance.getClass();

    if (acceptanceClass.isAssignableFrom(oldAcceptanceClass)) {
      @SuppressWarnings("unchecked")
      var castedAutomaton = (Automaton<S, B>) automaton;
      return castedAutomaton;
    }

    if (acceptanceClass.equals(EmersonLeiAcceptance.class)) {
      return new CastedAutomaton<>(
        (Automaton<S, ?>) automaton, acceptanceClass
        .cast(new EmersonLeiAcceptance(((Automaton<S, ?>) automaton).acceptance())), null);
    }

    var edgeCast = edgeCastMap(oldAcceptanceClass).get(acceptanceClass);
    return new CastedAutomaton<>((Automaton<S, ?>) automaton,
      cast(oldAcceptance, oldAcceptanceClass, acceptanceClass),
      edgeCast == null ? null : edge -> {
        BitSet set = edge.acceptanceSets();
        edgeCast.accept(oldAcceptance, set);
        return edge.withAcceptance(set);
      });
  }

  /**
   * Find heuristically the weakest acceptance condition for the given automaton and cast it to it.
   * Only simple syntactic checks on the boolean expression of the acceptance conditions are
   * performed. For advanced (and complete) techniques use the typeness implementations.
   *
   * @param automaton The automaton. It is assumed that after calling this method the automaton is
   *     not modified anymore.
   * @param <S> The state type.
   * @return A view on the given automaton with the necessary changes.
   */
  public static <S> Automaton<S, ?> castHeuristically(Automaton<S, ?> automaton) {
    var expression = automaton.acceptance().booleanExpression();

    if (expression.isFALSE()) {
      return EmptyAutomaton.of(automaton.factory(), AllAcceptance.INSTANCE);
    }

    if (detectAllAcceptance(expression)) {
      return new CastedAutomaton<>(automaton, AllAcceptance.INSTANCE, null);
    }

    if (detectBuechi(expression)) {
      return new CastedAutomaton<>(automaton, BuchiAcceptance.INSTANCE, null);
    }

    if (detectCoBuechi(expression)) {
      return new CastedAutomaton<>(automaton, CoBuchiAcceptance.INSTANCE, null);
    }

    var size1 = detectGeneralizedBuechi(expression);

    if (size1.isPresent()) {
      return new CastedAutomaton<>(automaton,
        GeneralizedBuchiAcceptance.of(size1.getAsInt()), null);
    }

    var size2 = detectGeneralizedCoBuechi(expression);

    if (size2.isPresent()) {
      return new CastedAutomaton<>(automaton,
        GeneralizedCoBuchiAcceptance.of(size2.getAsInt()), null);
    }

    var mapping = new int[automaton.acceptance().acceptanceSets()];
    var size3 = detectRabin(expression, mapping);

    if (size3.isPresent()) {
      return new CastedAutomaton<>(automaton, RabinAcceptance.of(size3.getAsInt()),
      edge -> edge.withAcceptance(x -> mapping[x]));
    }

    return automaton;
  }

  public static <A extends OmegaAcceptance, B extends OmegaAcceptance> B
    cast(A acceptance, Class<B> acceptanceClass) {

    @SuppressWarnings("unchecked")
    var oldAcceptanceClass = (Class<A>) acceptance.getClass();

    if (acceptanceClass.isAssignableFrom(oldAcceptanceClass)) {
      return acceptanceClass.cast(acceptance);
    }

    if (acceptanceClass.equals(EmersonLeiAcceptance.class)) {
      return acceptanceClass.cast(new EmersonLeiAcceptance(acceptance));
    }

    return cast(acceptance, oldAcceptanceClass, acceptanceClass);
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

  private static <A extends OmegaAcceptance, B extends OmegaAcceptance>
    B cast(A acceptance, Class<A> oldClass, Class<B> newClass) {
    Function<A, OmegaAcceptance> cast = castMap(oldClass).get(newClass);

    if (cast == null) {
      throw new ClassCastException(String.format("Cannot cast %s (%s) to %s.",
        oldClass.getSimpleName(), acceptance.booleanExpression(), newClass.getSimpleName()));
    }

    return newClass.cast(cast.apply(acceptance));
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

  private static boolean detectAllAcceptance(BooleanExpression<AtomAcceptance> expression) {
    return expression.isTRUE();
  }

  private static boolean detectBuechi(BooleanExpression<AtomAcceptance> expression) {
    if (!expression.isAtom()) {
      return false;
    }

    var atom = expression.getAtom();
    return !atom.isNegated() && atom.getType() == AtomAcceptance.Type.TEMPORAL_INF;
  }

  private static OptionalInt detectGeneralizedBuechi(BooleanExpression<AtomAcceptance> expression) {
    var usedSets = new BitSet();
    var conjuncts = BooleanExpressions.getConjuncts(expression);

    for (var conjunct : conjuncts) {
      if (!detectBuechi(conjunct)) {
        return OptionalInt.empty();
      }

      var set = conjunct.getAtom().getAcceptanceSet();

      if (usedSets.get(set)) {
        return OptionalInt.empty();
      }

      usedSets.set(set);
    }

    if (usedSets.cardinality() == usedSets.length()) {
      return OptionalInt.of(usedSets.length());
    }

    return OptionalInt.empty();
  }

  private static boolean detectCoBuechi(BooleanExpression<AtomAcceptance> expression) {
    if (!expression.isAtom()) {
      return false;
    }

    var atom = expression.getAtom();
    return !atom.isNegated() && atom.getType() == AtomAcceptance.Type.TEMPORAL_FIN;
  }

  private static OptionalInt detectGeneralizedCoBuechi(
    BooleanExpression<AtomAcceptance> expression) {

    var usedSets = new BitSet();
    var disjuncts = BooleanExpressions.getDisjuncts(expression);

    for (var disjunct : disjuncts) {
      if (!detectCoBuechi(disjunct)) {
        return OptionalInt.empty();
      }

      var set = disjunct.getAtom().getAcceptanceSet();

      if (usedSets.get(set)) {
        return OptionalInt.empty();
      }

      usedSets.set(set);
    }

    if (usedSets.cardinality() == usedSets.length()) {
      return OptionalInt.of(usedSets.length());
    }

    return OptionalInt.empty();
  }

  private static OptionalInt detectRabinPair(BooleanExpression<AtomAcceptance> expression,
    int[] mapping, int base) {
    var conjuncts = BooleanExpressions.getConjuncts(expression);

    int infIndex = -1;
    int finIndex = -1;

    for (var conjunct : conjuncts) {
      var atom = conjunct.getAtom();

      if (atom == null || atom.isNegated()) {
        return OptionalInt.empty();
      }

      if (atom.getType() == AtomAcceptance.Type.TEMPORAL_FIN) {
        if (finIndex >= 0) {
          return OptionalInt.empty();
        }

        finIndex = atom.getAcceptanceSet();
      } else {
        assert atom.getType() == AtomAcceptance.Type.TEMPORAL_INF;

        if (infIndex >= 0) {
          return OptionalInt.empty();
        }

        infIndex = atom.getAcceptanceSet();
      }
    }

    if (finIndex >= 0 && infIndex >= 0) {
      if (mapping[finIndex] >= 0) {
        return OptionalInt.empty();
      }

      mapping[finIndex] = base;

      if (mapping[infIndex] >= 0) {
        return OptionalInt.empty();
      }

      mapping[infIndex] = base + 1;
      return OptionalInt.of(2);
    }

    return OptionalInt.empty();
  }

  private static OptionalInt detectRabin(
    BooleanExpression<AtomAcceptance> expression, int[] mapping) {

    Arrays.fill(mapping, -1);
    int base = 0;
    var disjuncts = BooleanExpressions.getDisjuncts(expression);

    for (var disjunct : disjuncts) {
      var offset = detectRabinPair(disjunct, mapping, base);

      if (offset.isPresent()) {
        base += offset.getAsInt();
      } else {
        return OptionalInt.empty();
      }
    }

    return OptionalInt.of(disjuncts.size());
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
