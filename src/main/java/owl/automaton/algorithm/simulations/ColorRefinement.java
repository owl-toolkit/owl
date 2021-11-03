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

package owl.automaton.algorithm.simulations;

import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.collections.BitSet2;
import owl.collections.Pair;

/**
 * Computes direct simulation relation of an automaton based on the color refinement algorithm. See
 * the constructor of for more info.
 *
 * @param <S> Type of state for the underlying automaton.
 */
public class ColorRefinement<S> {
  final Automaton<S, BuchiAcceptance> aut;
  final BddSetFactory factory;

  final Ordering po;
  Ordering oldPo;
  final Coloring<S> col;
  Coloring<S> oldCol;
  final Map<S, Pair<Integer, Neighborhood>> intermediate;

  private ColorRefinement(Automaton<S, ? extends BuchiAcceptance> automaton) {
    aut = (Automaton<S, BuchiAcceptance>) automaton;
    factory = aut.factory();
    intermediate = new HashMap<>();
    oldPo = new Ordering();
    oldCol = new Coloring<>();

    col = new Coloring<>();
    po = new Ordering();
    initialize();
  }

  /**
   * Actual computation of the algorithm, which refines based on neighborhoods associated with each
   * state until a fixed point is reached.
   *
   * @return The set of pairs of direct-similar states.
   */
  private Set<Pair<S, S>> refine() {
    int iterations = 0;
    // iterate until a fixed point is reached
    while (!po.equals(oldPo) || !col.equals(oldCol)) {
      // ensure that the algorithm terminates eventually, usually under 50 iterations are enough
      // even for pretty large automata
      if (iterations > 100_000) {
        throw new AssertionError("refinement should terminate eventually");
      }
      iterations += 1;
      // back up old colors and ordering
      oldCol = Coloring.copyOf(col);
      oldPo = Ordering.copyOf(po);

      // clear and compute new intermediate coloring
      for (var state : aut.states()) {
        intermediate.put(state, Pair.of(
          oldCol.get(state), Neighborhood.maximal(aut, state, oldPo, oldCol)
        ));
      }

      // normalize colors by sorting with regard to the lexicographic ordering
      ArrayList<Pair<Integer, Neighborhood>> tmp = intermediate.values()
        .stream().distinct().sorted((p1, p2) -> {
          // first try to sort based on color in first entry
          if (!p1.fst().equals(p2.fst())) {
            return p1.fst().compareTo(p2.fst());
          }
          return p1.snd().compareTo(p2.snd());
        }).collect(Collectors.toCollection(ArrayList::new));

      // assign to each intermediate color a normalized, integer color
      Map<Pair<Integer, Neighborhood>, Integer> norm = new HashMap<>();
      int i = 0;
      for (var e : tmp) {
        norm.put(e, i++);
      }

      // produce new coloring
      col.clear();
      for (var state : aut.states()) {
        col.set(state, norm.get(intermediate.get(state)));
      }

      // compute the new partial ordering
      po.clear();

      for (var state1 : aut.states()) {
        Pair<Integer, Neighborhood> cc1 = intermediate.get(state1);
        for (var state2 : aut.states()) {
          Pair<Integer, Neighborhood> cc2 = intermediate.get(state2);

          if (oldPo.leq(cc2.fst(), cc1.fst())
            && cc1.snd().dominates(oldPo, cc2.snd())) {
            po.set(col.get(state2), col.get(state1));
          }
        }
      }
    }

    var out = new HashSet<Pair<S, S>>();
    for (var state1 : aut.states()) {
      for (var state2 : aut.states()) {
        if (po.leq(col.get(state1), col.get(state2))) {
          out.add(Pair.of(state1, state2));
        }
      }
    }
    return out;
  }

  /**
   * Initializes coloring by grouping states based on the set of valuations with which they can
   * take an accepting transition. Builds an ordering based on those sets, where a color is subsumed
   * if the associated maximal valuation set is implied by the one associated with another color.
   */
  private void initialize() {
    Map<S, BddSet> maxAcceptingVal = new HashMap<>();
    Map<S, BddSet> availableVal = new HashMap<>();
    Map<S, Pair<BddSet, BddSet>> interm = new HashMap<>();
    Map<Pair<BddSet, BddSet>, Integer> intermC = new HashMap<>();
    Map<BddSet, Integer> setColouring = new HashMap<>();
    Map<Integer, Pair<BddSet, BddSet>> rIntermC = new HashMap<>();

    // we iterate over all states and possible valuations to create something like a transition
    // profile for each state. We collect all symbols on which an accepting transition is available
    // to initialize the colouring.
    for (S state : aut.states()) {
      maxAcceptingVal.put(state, aut.factory().of(false));

      Map<Edge<S>, BddSet> edgeMap = aut.edgeMap(state);

      edgeMap.forEach((edge, valuation) -> {
        if (aut.acceptance().isAcceptingEdge(edge)) {
          maxAcceptingVal.computeIfPresent(state, (key, oldValue) -> valuation.union(oldValue));
        }
      });

      availableVal.put(state, aut.factory().of(false));

      edgeMap.forEach((edge, vSet) -> {
        availableVal.put(state, availableVal.get(state).union(vSet));
      });

      interm.put(state, Pair.of(availableVal.get(state), maxAcceptingVal.get(state)));
    }

    // collect all distinct valuation sets and assign increasing colours to them
    int i = 0;
    for (var set : maxAcceptingVal.values()) {
      if (!setColouring.containsKey(set)) {
        setColouring.put(set, i++);
      }
    }

    int j = 0;
    for (var p : interm.values()) {
      if (!intermC.containsKey(p)) {
        intermC.put(p, j);
        rIntermC.put(j++, p);
      }
    }

    // 'combine' the two mappings to obtain the initial colouring for the automaton
    interm.forEach((key, value) -> col.set(key, intermC.get(value)));

    for (int c : col.values()) {
      for (Map.Entry<Integer, Pair<BddSet, BddSet>> entry : rIntermC.entrySet()) {
        var p1 = rIntermC.get(c);
        var p2 = entry.getValue();
        boolean contained = p2.fst().containsAll(p1.fst());

        if (contained && p2.snd().containsAll(p1.snd())) {
          po.set(c, entry.getKey());
        }
      }
    }
  }


  /**
   * Computes the color refinement (i.e. direct simulation relation) for a given automaton. Based
   * on the algorithm "StrongFairSimulationReduction" presented in "Optimizing Buchi Automata" by
   * Etessami and Holzmann in 2000.
   *
   * @param automaton The automaton to compute the refinement for.
   * @param <S> The type of state in the automaton.
   * @return A set of direct-similar pairs of states.
   */
  public static <S> Set<Pair<S, S>> of(Automaton<S, ? extends BuchiAcceptance> automaton) {
    return new ColorRefinement<>(automaton).refine();
  }

  /**
   * Represents a neighbor type consisting of color and associated valuation.
   */
  @AutoValue
  public abstract static class NeighborType {
    public abstract int colour();

    // conversion to int and back is needed as AutoValue acts up otherwise
    public abstract int valuation();

    public abstract boolean accepting();

    public abstract BddSetFactory factory();

    public static NeighborType of(int colour, BitSet set, BddSetFactory factory, boolean a) {
      return new AutoValue_ColorRefinement_NeighborType(
        colour, BitSet2.toInt(set), a, factory
      );
    }

    /**
     * Tests whether the neighbor type dominates another one, which holds if the other color is
     * subsumed wrt. the ordering and the valuations indicate that whenever the other transition
     * can be taken, this transition can also be taken.
     *
     * @param other The other neighbor type.
     * @param ord Ordering to compare against.
     * @return true if and only if this neighborhood dominates the other.
     */
    public boolean dominates(
      NeighborType other,
      Ordering ord
    ) {
      return ((ord.leq(other.colour(), colour()))
        // check if other valuation implies ours
        && other.valuation() == valuation()
        && (!other.accepting() || accepting()));
    }

    @Override
    public String toString() {
      return "("
        + colour()
        + ", \""
        + BitSet2.fromInt(valuation())
        + "\") ";
    }
  }

  /**
   * Represents the neighborhood of a state. A neighborhood consists of all NeighborTypes that are
   * maximal with regard to the ordering computed in the previous iteration.
   */
  @AutoValue
  public abstract static class Neighborhood {
    public abstract Set<NeighborType> types();

    public abstract BddSet available();

    public static Neighborhood of(Set<NeighborType> nts, BddSet available) {
      return new AutoValue_ColorRefinement_Neighborhood(nts, available);
    }

    /**
     * Compares a neighborhood to another such that they can be sorted.
     *
     * @param other The neighborhood to compare against.
     * @return 0 if they are equal, -1 if the other is larger, 1 otherwise.
     */
    public int compareTo(Neighborhood other) {
      if (equals(other)) {
        return 0;
      }
      // if the other neighborhood has more types we say it is larger
      if (types().size() < other.types().size()) {
        return -1;
      } else if (types().size() > other.types().size()) {
        return 1;
      }
      // collect all colors that occur in the two neighborhoods
      var allOcc = types().stream().map(NeighborType::colour)
        .collect(Collectors.toCollection(ArrayList::new));
      var allOtherOcc = other.types().stream()
        .map(NeighborType::colour).collect(Collectors.toCollection(ArrayList::new));
      // sort them wrt natural number comparison
      allOcc.sort(Integer::compareTo);
      allOtherOcc.sort(Integer::compareTo);
      // iterate the sorted lists and return comparison of first distinct elements
      for (int i = 0; i < Math.min(allOcc.size(), allOtherOcc.size()); i++) {
        if (!allOcc.get(i).equals(allOtherOcc.get(i))) {
          return allOcc.get(i).compareTo(allOtherOcc.get(i));
        }
      }
      return 0;
    }

    /**
     * Constructs a neighborhood for a given state under a previously computed coloring and ordering
     * consisting of only maximal NeighborTypes.
     *
     * @param aut The underlying automaton.
     * @param state The state to compute the maximal Neighborhood for.
     * @param ord Ordering with regard to which maximality is tested.
     * @param col The coloring associated with the Ordering.
     * @param <S> Type of state of the automaton.
     * @return A maximal neighborhood where no type is dominated by another.
     */
    public static <S> Neighborhood maximal(
      Automaton<S, BuchiAcceptance> aut,
      S state,
      Ordering ord,
      Coloring<S> col
    ) {
      // collect all possible neighbor types
      HashSet<NeighborType> nts = new HashSet<>();

      BddSet avail = aut.factory().of(false);
      for (var e : aut.edgeMap(state).entrySet()) {
        avail = avail.union(e.getValue());
      }

      // important to split valuation sets and extract each possible valuation as otherwise it can
      // happen that one valuation of a set is covered by two distinct transitions later on, which
      // the algorithm could otherwise not pick up on.
      aut.edgeMap(state).forEach((edge, valSet) -> valSet
        .iterator(aut.atomicPropositions().size()).forEachRemaining(val -> {
          nts.add(NeighborType.of(
            col.get(edge.successor()),
            val,
            aut.factory(),
            aut.acceptance().isAcceptingEdge(edge)
          ));
        }));
      // filter out all neighbor types that are dominated
      var maximal = nts.stream()
        .filter(nt -> nts.stream()
          .filter(ntt -> !nt.equals(ntt))
          .noneMatch(ntt -> ntt.dominates(nt, ord)))
        .collect(Collectors.toSet());
      return Neighborhood.of(maximal, avail);
    }

    /**
     * Tests whether the neighborhood dominates another, which holds if each neighbor type in the
     * other neighborhood is dominated by one in this neighborhood.
     *
     * @param ord The order to compare against.
     * @param other The other neighborhood that is dominated.
     * @return True if and only if this neighborhood dominates the other.
     */
    public boolean dominates(
      Ordering ord,
      Neighborhood other
    ) {
      if (equals(other)) {
        return true;
      }
      // iterate all types in the other neighborhood and test if we find a dominating one
      for (var nt : other.types()) {
        boolean isDom = types().stream()
          .anyMatch(t -> t.dominates(nt, ord));
        if (!isDom) {
          return false;
        }
      }

      return available().containsAll(other.available());
    }
  }

  /**
   * Represents a coloring of the state set of a Buchi automaton.
   *
   * @param <S> Type of state of the underlying automaton.
   */
  private static class Coloring<S> {
    private final Map<S, Integer> col;

    public Coloring() {
      col = new HashMap<>();
    }

    /**
     * Computes the image of the coloring.
     *
     * @return The set of used colors.
     */
    public Collection<Integer> values() {
      return col.values();
    }

    @Override
    public int hashCode() {
      return col.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof Coloring<?> other) {
        return col.equals(other.col);
      }
      return false;
    }

    public void clear() {
      col.clear();
    }

    public static <S> Coloring<S> copyOf(Coloring<S> other) {
      var out = new Coloring<S>();
      out.col.putAll(other.col);
      return out;
    }

    /**
     * Colors a state.
     *
     * @param state State to be colored.
     * @param color Color that shall be assigned to the state.
     */
    public void set(S state, Integer color) {
      col.put(state, color);
    }

    /**
     * Obtains the color of a given state.
     *
     * @param state The state for which the color should be extracted.
     * @return The color assigned to the state.
     */
    public Integer get(S state) {
      assert col.containsKey(state);
      return col.get(state);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<S, Integer> entry : col.entrySet()) {
        sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append('\n');
      }
      return sb.toString();
    }
  }

  /**
   * Represents a partial order on a coloring. Internally represented as a map from color to a set
   * of color that are larger wrt to the ordering, i.e. colors dominating it.
   */
  private static class Ordering {
    private final Map<Integer, Set<Integer>> ord;

    public Ordering() {
      ord = new HashMap<>();
    }

    @Override
    public int hashCode() {
      return ord.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof Ordering other) {
        return other.ord.equals(this.ord);
      }
      return false;
    }

    public int size() {
      return ord.entrySet().size();
    }

    public void clear() {
      ord.clear();
    }

    public static Ordering copyOf(Ordering other) {
      var out = new Ordering();
      out.ord.putAll(other.ord);
      return out;
    }

    /**
     * Inserts a pair (a, b) into the order where a <= b.
     *
     * @param a Subsumed/dominated element.
     * @param b Subsuming/dominating element.
     */
    public void set(Integer a, Integer b) {
      var curr = ord.getOrDefault(a, new HashSet<>());
      curr.add(b);
      ord.put(a, curr);
    }

    /**
     * Tests whether a color is in relation with another color.
     *
     * @param a First, 'smaller' color.
     * @param b 'Larger' color.
     * @return true if and only if a <= b.
     */
    public boolean leq(Integer a, Integer b) {
      if (a.equals(b)) {
        return true;
      }
      return ord.getOrDefault(a, Set.of()).contains(b);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("ordering size: ").append(size()).append('\n');
      for (Map.Entry<Integer, Set<Integer>> entry : ord.entrySet()) {
        sb.append(entry.getKey()).append(" <= ").append(entry.getValue()).append('\n');
      }
      return sb.toString();
    }
  }
}
