/*
 * Copyright (C) 2016 - 2022  (See AUTHORS)
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

package owl.automaton.minimization;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import owl.automaton.AbstractMemoizingAutomaton.EdgesImplementation;
import owl.automaton.Automaton;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaReader;
import owl.automaton.minimization.GfgNcwMinimization.CanonicalGfgNcw;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

public class DcwRepository {

  public final static List<DcwMinimisationTestCase> GFG_NCW_HELPFUL_EXAMPLES;

  static {
    List<DcwMinimisationTestCase> builder = new ArrayList<>();

    builder.add(new DcwMinimisationTestCase("""
        HOA: v1
        tool: "owl" "21.1-development"
        Start: 0
        acc-name: co-Buchi 1
        Acceptance: 1 Fin(0)
        properties: trans-acc no-univ-branch\s
        properties: complete\s
        AP: 1 "a"
        --BODY--
        State: 0
        [0] 1 {0}
        [0] 2 {0}
        [0] 3 {0}
        [!0] 4
        [0] 4 {0}
        [0] 0 {0}
        State: 1
        [!0] 1
        [0] 2
        State: 2
        [!0] 3
        [0] 4
        State: 3
        [!0] 1
        [0] 1 {0}
        [0] 2 {0}
        [0] 3 {0}
        [0] 4 {0}
        [0] 0 {0}
        State: 4
        [!0] 2
        [0] 0
        --END--
        """, 5, 8, 1, 1));

    builder.add(new DcwMinimisationTestCase("""
        HOA: v1
        Start: 0
        acc-name: co-Buchi 1
        Acceptance: 1 Fin(0)
        properties: trans-acc no-univ-branch
        properties: complete
        AP: 2 "a" "b"
        --BODY--
        State: 0
        [0] 1
        [!0] 0
        State: 1
        [t] 2
        State: 2
        [0 & !1] 2 {0}
        [0 & 1] 1
        [0 & !1] 1 {0}
        [!0] 0
        [0 & !1] 0 {0}
        --END--
        """, 3, 4, 1, 1));

    builder.add(new DcwMinimisationTestCase("""
        HOA: v1
        Start: 0
        acc-name: co-Buchi 1
        Acceptance: 1 Fin(0)
        properties: trans-acc no-univ-branch complete
        AP: 1 "a"
        Alias: @a !0
        Alias: @b  0
        --BODY--
        State: 0
        [@a] 1
        [@b] 3
        State: 1 "bb"
        [@a] 4
        [@b] 2
        State: 2
        [@a] 3 {0}
        [@b] 1
        State: 3 "ab+ba"
        [@a] 0
        [@b] 4
        State: 4
        [@a] 3
        [@b] 1 {0}
        [@b] 3 {0}
        --END--
        """, 5, 6, 2, 1));

    builder.add(new DcwMinimisationTestCase("""
        HOA: v1
        Start: 0
        acc-name: co-Buchi 1
        Acceptance: 1 Fin(0)
        properties: trans-acc no-univ-branch\s
        properties: complete\s
        AP: 1 "a"
        --BODY--
        State: 0
        [!0] 1
        [0] 2
        State: 1
        [t] 3
        State: 2
        [!0] 1 {0}
        [!0] 4 {0}
        [0] 4
        [!0] 2 {0}
        [!0] 3 {0}
        [!0] 0 {0}
        State: 3
        [t] 2
        State: 4
        [!0] 2
        [0] 0
        --END--
        """, 5, 6, 1, 1));

    builder.add(new DcwMinimisationTestCase("""
        HOA: v1
        tool: "owl" "21.1-development"
        Start: 0
        acc-name: co-Buchi 1
        Acceptance: 1 Fin(0)
        properties: trans-acc no-univ-branch
        properties: complete
        AP: 1 "a"
        --BODY--
        State: 0
        [!0] 1
        [0] 2
        State: 1
        [0] 3
        [!0] 0
        State: 2
        [0] 4 {0}
        [0] 1 {0}
        [0] 3 {0}
        [0] 5 {0}
        [0] 2 {0}
        [0] 6 {0}
        [0] 0 {0}
        [!0] 7
        [0] 7 {0}
        [0] 8 {0}
        State: 3
        [!0] 4 {0}
        [0] 4
        [!0] 1 {0}
        [!0] 3 {0}
        [!0] 5 {0}
        [!0] 2 {0}
        [!0] 6 {0}
        [!0] 0 {0}
        [!0] 7 {0}
        [!0] 8 {0}
        State: 4
        [!0] 4 {0}
        [!0] 1 {0}
        [!0] 3 {0}
        [!0] 5 {0}
        [!0] 2 {0}
        [!0] 6 {0}
        [!0] 0 {0}
        [!0] 7 {0}
        [!0] 8 {0}
        [0] 8
        State: 5
        [0] 5
        [!0] 6
        State: 6
        [!0] 4 {0}
        [!0] 1 {0}
        [!0] 3 {0}
        [!0] 5 {0}
        [0] 5
        [!0] 2 {0}
        [!0] 6 {0}
        [!0] 0 {0}
        [!0] 7 {0}
        [!0] 8 {0}
        State: 7
        [!0] 0
        [0] 8
        State: 8
        [t] 7
        --END--
        """, 9, 10, 1, 2));

    builder.add(new DcwMinimisationTestCase("""
        HOA: v1
        Start: 0
        acc-name: co-Buchi 1
        Acceptance: 1 Fin(0)
        properties: trans-acc no-univ-branch complete
        AP: 2 "p0" "p1"
        Alias: @a !0 & !1
        Alias: @b  0 & !1
        Alias: @c       1
        --BODY--
        State: 0 "cc-loop"
        [@a] 4
        [@b] 6
        [@c] 3
        State: 1 "ac/ca-loop"
        [@a] 5
        [@b] 3 {0}
        [@c] 8
        State: 2 "bc/cb-loop"
        [@a] 3 {0}
        [@b] 7
        [@c] 9
        State: 3 "cc-companion"
        [@a] 1 {0}
        [@b] 2 {0}
        [@c] 0
        State: 4 "0 -> 1 (from 0)"
        [@a] 1
        [@b] 2 {0}
        [@c] 1 {0}
        State: 8 "0 -> 1 (from 1)"
        [@a] 1
        [@b] 2 {0}
        [@c] 0 {0}
        State: 5 "1 -> 0"
        [@a] 0
        [@b] 0 {0}
        [@c] 1
        State: 6 "0 -> 2 (from 0)"
        [@a] 1 {0}
        [@b] 2
        [@c] 2 {0}
        State: 9 "0 -> 2 (from 2)"
        [@a] 1 {0}
        [@b] 2
        [@c] 0 {0}
        State: 7 "2 -> 0"
        [@a] 0 {0}
        [@b] 0
        [@c] 2
        --END--
        """, 8, 10, 2, 1));

    GFG_NCW_HELPFUL_EXAMPLES = List.copyOf(builder);

    checkState(GFG_NCW_HELPFUL_EXAMPLES.stream()
        .allMatch(x -> x.canonicalGfgNcw.alphaMaximalGfgNcw.states().size() < x.minimalDcwSize));
  }


  private static final Map<Integer, Integer> PERMUTATION_LANGUAGE_DCW_SIZE = Map.ofEntries(
      Map.entry(2, 2),
      Map.entry(3, 6),
      Map.entry(4, 14),
      Map.entry(5, 30));

  public static DcwMinimisationTestCase permutationLanguageTestCase(int i) {
    var automaton = permutationLanguage(i);
    int dcwSize = PERMUTATION_LANGUAGE_DCW_SIZE.get(i);
    return new DcwMinimisationTestCase(
        automaton,
        automaton.states().size(),
        dcwSize,
        1,
        1);
  }

  public static List<DcwMinimisationTestCase> fromDb() throws IOException, ParseException {

    List<DcwMinimisationTestCase> testCases = new ArrayList<>();

    try (var directory = Files.newDirectoryStream(
        Path.of("/Users/bob/Teleporter/Fedora Workstation 35/docker/merge/crystal/unique"))) {

      for (Path path : directory) {
        List<Automaton<Integer, ?>> automata = HoaReader.readMultiple(path);

        for (var autoanton : automata) {
          var canonical = GfgNcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(
              OmegaAcceptanceCast.castExact(autoanton, CoBuchiAcceptance.class)));

          testCases.add(new DcwMinimisationTestCase(canonical,
              canonical.alphaMaximalGfgNcw.states().size() + 1));
        }
      }
    }

    testCases.sort(
        Comparator.comparingInt(x -> x.canonicalGfgNcw.alphaMaximalGfgNcw.states().size()));
    return testCases;
  }

  public static Stream<Automaton<Integer, CoBuchiAcceptance>> randomAutomataStream(
      SplittableRandom seed,
      int apSize,
      int safeComponents,
      int safeComponentSize) {

    Function<RandomGenerator, Automaton<Integer, CoBuchiAcceptance>> automatonSupplier = random -> {
      Map<Integer, MtBdd<Edge<Integer>>> nonAlphaTransitions = new HashMap<>();
      ImmutableBitSet allStates = ImmutableBitSet.range(0, safeComponents * safeComponentSize);

      for (int i = 0; i < safeComponents; i++) {
        var safeComponent
            = ImmutableBitSet.range(i * safeComponentSize, (i + 1) * safeComponentSize);
        nonAlphaTransitions.putAll(randomSafeComponent(apSize, safeComponent, random));
      }

      return new EdgesImplementation<>(
          IntStream.range(0, apSize).mapToObj(i -> "a" + i).toList(),
          Set.of(0),
          CoBuchiAcceptance.INSTANCE) {

        @Override
        protected Set<Edge<Integer>> edgesImpl(Integer state, BitSet valuation) {
          var edgeTree = nonAlphaTransitions.get(state);
          var edges = edgeTree.get(valuation);
          assert edges.size() <= 1;
          return edges.isEmpty() ? allStates.intStream().mapToObj(i -> Edge.of(i, 0))
              .collect(Collectors.toUnmodifiableSet()) : edges;
        }
      };
    };

    return seed.rngs().map(automatonSupplier);
  }

  private static Map<Integer, MtBdd<Edge<Integer>>>
  randomSafeComponent(int apSize, ImmutableBitSet states, RandomGenerator random) {

    Integer[] statesArray = states.toArray(Integer[]::new);
    BddSetFactory factory = FactorySupplier.defaultSupplier().getBddSetFactory();
    Map<Integer, MtBdd<Edge<Integer>>> edgeTrees = new HashMap<>();

    for (Integer state : statesArray) {
      Map<Edge<Integer>, BddSet> edgeMap = new HashMap<>();

      for (BitSet sigma : BitSet2.powerSet(apSize)) {
        boolean alphaEdge = random.nextInt(10) < 3;

        if (!alphaEdge) {
          int successor = statesArray[random.nextInt(statesArray.length)];
          edgeMap.compute(Edge.of(successor),
              (Edge<Integer> key, BddSet oldValue) -> oldValue == null ? factory.of(sigma, apSize)
                  : oldValue.union(factory.of(sigma, apSize)));
        }
      }

      edgeTrees.put(state, factory.toMtBdd(edgeMap));
    }

    return edgeTrees;
  }

  // Denis Kuperberg, Michał Skrzypczak: "On Determinisation of Good-for-Games Automata"
  static Automaton<Integer, CoBuchiAcceptance> permutationLanguage(int n) {

    return new EdgesImplementation<>(
        List.of("a", "b"), Set.of(1), CoBuchiAcceptance.INSTANCE) {

      @Override
      public Set<Edge<Integer>> edgesImpl(Integer index, BitSet valuation) {
        if (valuation.get(0)) {
          int newIndex = index + 1;

          if (newIndex > n) {
            newIndex = 1;
          }

          return Set.of(Edge.of(newIndex));
        } else if (valuation.get(1)) {
          if (index == 1) {
            return IntStream.range(1, n + 1)
                .mapToObj(x -> Edge.of(x, 0))
                .collect(Collectors.toSet());
          }

          return Set.of(Edge.of(index));
        } else {
          if (index.equals(1)) {
            return Set.of(Edge.of(2));
          } else if (index.equals(2)) {
            return Set.of(Edge.of(1));
          } else {
            return Set.of(Edge.of(index));
          }
        }
      }
    };
  }

  public record DcwMinimisationTestCase(CanonicalGfgNcw canonicalGfgNcw, int minimalDcwSize) {

    public DcwMinimisationTestCase {
      checkArgument(canonicalGfgNcw.alphaMaximalGfgNcw.states().size() <= minimalDcwSize);
    }

    public DcwMinimisationTestCase(
        String ncw,
        int canonicalGfgNcwSize,
        int minimalDcwSize,
        int equivalenceClasses,
        int safeComponents) {

      this(parseNcw(ncw), canonicalGfgNcwSize, minimalDcwSize, equivalenceClasses, safeComponents);
    }

    public DcwMinimisationTestCase(
        Automaton<?, CoBuchiAcceptance> ncw,
        int canonicalGfgNcwSize,
        int minimalDcwSize,
        int equivalenceClasses,
        int safeComponents) {

      this(canonicalGfgNcw(ncw), minimalDcwSize);

      checkArgument(canonicalGfgNcw.alphaMaximalGfgNcw.states().size() == canonicalGfgNcwSize,
          "Canonical Gfg-tNcw (expected: %s, actual: %s)".formatted(
              canonicalGfgNcwSize,
              canonicalGfgNcw.alphaMaximalGfgNcw.states().size()));

      checkArgument(canonicalGfgNcw.languageEquivalenceClasses.size() == equivalenceClasses,
          "Language Equivalence Class (expected: %s, actual: %s)".formatted(
              equivalenceClasses,
              canonicalGfgNcw.languageEquivalenceClasses.size()));

      checkArgument(canonicalGfgNcw.safeComponents.size() == safeComponents);
    }

    private static CanonicalGfgNcw canonicalGfgNcw(Automaton<?, CoBuchiAcceptance> ncw) {
      return GfgNcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(ncw));
    }

    private static Automaton<Integer, CoBuchiAcceptance> parseNcw(String language) {
      try {
        return OmegaAcceptanceCast.castExact(HoaReader.read(language), CoBuchiAcceptance.class);
      } catch (ParseException e) {
        throw new IllegalArgumentException(e);
      }
    }

    @Override
    public String toString() {
      return "DcwMinTestCase{gfgNcwSize=%s, minimalDcwSize=%d, equivalenceClasses=%s, subsafeEquivalentRelation=%s, safeComponents=%s}".formatted(
          canonicalGfgNcw.alphaMaximalGfgNcw.states().size(),
          minimalDcwSize,
          canonicalGfgNcw.languageEquivalenceClasses,
          canonicalGfgNcw.subsafeEquivalentRelation,
          canonicalGfgNcw.safeComponents);
    }
  }
}
