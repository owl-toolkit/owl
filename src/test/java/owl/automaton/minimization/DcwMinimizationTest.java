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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaReader;
import owl.automaton.hoa.HoaWriter;
import owl.automaton.minimization.DcwRepository.DcwMinimisationTestCase;
import owl.automaton.minimization.GfgNcwMinimization.CanonicalGfgNcw;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.Pair;
import owl.command.AutomatonConversionCommands;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.nbadet.NbaDet;

class DcwMinimizationTest {

  private static final String EXAMPLE_1 = """
      HOA: v1
      States: 2
      Start: 0
      AP: 1 "a"
      acc-name: coBuchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [0] 0
      [!0] 0 {0}
      [!0] 1 {0}
      State: 1
      [!0] 1
      [0] 0 {0}
      [0] 1 {0}
      --END--
      """;

  private static final String EXAMPLE_2 = """
      HOA: v1
      States: 3
      Start: 0
      AP: 2 "a" "b"
      acc-name: coBuchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [ 0] 0
      [!0] 0 {0}
      [!0] 1 {0}
      [!0] 2 {0}
      State: 1
      [!0 &  1] 1
      [!0 & !1] 2
      [0] 0 {0}
      [0] 1 {0}
      [0] 2 {0}
      State: 2
      [!0 &  1] 1
      [!0 & !1] 2
      [0] 0 {0}
      [0] 1 {0}
      [0] 2 {0}
      --END--
      """;

  private static final String HOA_GFG_CO_BUCHI = """
      HOA: v1
      States: 4
      Start: 0
      AP: 1 "a"
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [ 0] 0
      [!0] 1
      State: 1
      [ 0] 0 {0}
      [ 0] 1 {0}
      [ 0] 2 {0}
      [ 0] 3 {0}
      [!0] 2
      State: 2
      [ t] 3
      State: 3
      [ 0] 0
      [!0] 0 {0}
      [!0] 1 {0}
      [!0] 2 {0}
      [!0] 3 {0}
      --END--""";

  private static final String NEXT_EXAMPLE = """
      HOA: v1
      States: 5
      Start: 0
      AP: 1 "a"
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [!0] 1
      [ 0] 2
      State: 1
      [ t] 3
      State: 2
      [ t] 4
      State: 3
      [!0] 0
      [ 0] 0 {0}
      [ 0] 1 {0}
      [ 0] 2 {0}
      [ 0] 3 {0}
      [ 0] 4 {0}
      State: 4
      [ 0] 0
      [!0] 0 {0}
      [!0] 1 {0}
      [!0] 2 {0}
      [!0] 3 {0}
      [!0] 4 {0}
      --END--""";

  private static final String NEXT_EXAMPLE_2 = """
      HOA: v1
      States: 5
      Start: 0
      AP: 1 "a"
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [!0] 1
      [ 0] 2
      State: 1
      [ t] 3
      State: 2
      [ t] 4
      State: 3
      [!0] 0
      [ 0] 3 {0}
      [ 0] 4 {0}
      State: 4
      [ 0] 0
      [!0] 3 {0}
      [!0] 4 {0}
      --END--""";

  private static final String THREE_COMPONENTS = """
      HOA: v1
      States: 9
      Start: 0
      Start: 2
      Start: 1
      Start: 4
      Start: 3
      Start: 5
      Start: 6
      Start: 7
      Start: 8
      AP: 2 "a" "b"
      Alias: @a !0 & !1
      Alias: @b !0 &  1
      Alias: @c  0
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [@a] 1
      [@b] 2
      State: 1
      [@a] 0
      State: 2
      [@b] 0
      State: 3
      [@a] 4
      [@c] 5
      State: 4
      [@a] 3
      State: 5
      [@c] 3
      State: 6
      [@b] 7
      [@c] 8
      State: 7
      [@b] 6
      State: 8
      [@c] 6
      --END--""";

  private static final String THREE_COMPONENTS_2 = """
      HOA: v1
      States: 9
      Start: 0
      Start: 2
      Start: 1
      Start: 4
      Start: 3
      Start: 5
      Start: 6
      Start: 7
      Start: 8
      AP: 2 "a" "b"
      Alias: @a !0 & !1
      Alias: @b !0 &  1
      Alias: @c  0
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [@a] 1
      [@b] 2
      State: 1
      [@b] 0
      State: 2
      [@a] 0
      State: 3
      [@a] 4
      [@c] 5
      State: 4
      [@c] 3
      State: 5
      [@a] 3
      State: 6
      [@b] 7
      [@c] 8
      State: 7
      [@c] 6
      State: 8
      [@b] 6
      --END--""";

  private static final String FOSSACS_EXAMPLE = """
      HOA: v1
      States: 9
      Start: 0
      AP: 2 "x" "y"
      Alias: @a !0 & !1
      Alias: @b !0 &  1
      Alias: @c  0
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [@a] 2
      [@b] 1 {0}
      [@c] 0
      State: 1
      [@a] 0 {0}
      [@b] 1 {0}
      [@c] 1 {0}
      State: 2
      [@a] 2
      [@b] 3
      [@c] 2
      State: 3
      [@a] 2
      [@b] 1 {0}
      [@c] 3
      --END--""";

  private static final String FOSSACS_EXAMPLE_SIMPLE = """
      HOA: v1
      States: 9
      Start: 0
      AP: 1 "a"
      Alias: @a  0
      Alias: @b !0
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [@a] 2
      [@b] 1 {0}
      State: 1
      [@a] 0 {0}
      [@b] 1 {0}
      State: 2
      [@a] 2
      [@b] 3
      State: 3
      [@a] 2
      [@b] 1 {0}
      --END--""";

  private static final String ELEVATOR = """
      HOA: v1
      States: 6
      Start: 0
      AP: 1 "a"
      acc-name: co-Buchi
      Acceptance: 1 Fin(0)
      --BODY--
      State: 0
      [ 0] 0
      [ t] 0 {0}
      [ t] 1 {0}
      [ t] 2 {0}
      [ t] 3 {0}
      [ t] 4 {0}
      [ t] 5 {0}
      State: 1
      [!0] 2
      [ t] 0 {0}
      [ t] 1 {0}
      [ t] 2 {0}
      [ t] 3 {0}
      [ t] 4 {0}
      [ t] 5 {0}
      State: 2
      [ 0] 1
      [ t] 0 {0}
      [ t] 1 {0}
      [ t] 2 {0}
      [ t] 3 {0}
      [ t] 4 {0}
      [ t] 5 {0}
      State: 3
      [!0] 4
      [ t] 0 {0}
      [ t] 1 {0}
      [ t] 2 {0}
      [ t] 3 {0}
      [ t] 4 {0}
      [ t] 5 {0}
      State: 4
      [!0] 5
      [ t] 0 {0}
      [ t] 1 {0}
      [ t] 2 {0}
      [ t] 3 {0}
      [ t] 4 {0}
      [ t] 5 {0}
      State: 5
      [ 0] 3
      [ t] 0 {0}
      [ t] 1 {0}
      [ t] 2 {0}
      [ t] 3 {0}
      [ t] 4 {0}
      [ t] 5 {0}
      --END--""";

  private static final String FOO = """
      HOA: v1
      tool: "owl" "21.1-development"
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch\s
      properties: deterministic unambiguous\s
      properties: complete\s
      AP: 2 "a" "b"
      --BODY--
      State: 0 "0"
      [0] 1 {0}
      [!0] 0 {}
      State: 1 "2"
      [0] 2 {}
      [!0] 3 {0}
      State: 2 "3"
      [0 & 1] 2 {}
      [!0] 3 {}
      [0 & !1] 0 {}
      State: 3 "1"
      [0] 1 {}
      [!0 & !1] 2 {}
      [!0 & 1] 0 {}
      --END--
      """;

  private static final String RR_Q4_AP1 = """
      HOA: v1
      tool: "owl" "21.1-development"
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch\s
      properties: deterministic unambiguous\s
      properties: complete\s
      AP: 1 "a"
      --BODY--
      State: 0
      [!0] 1 {}
      [0] 0 {0}
      State: 1
      [t] 2 {}
      State: 2
      [!0] 3 {0}
      [0] 0 {}
      State: 3
      [!0] 3 {}
      [0] 0 {}
      --END--""";

  private static final String GFG_NBW_EXAMPLE = """
      HOA: v1
      Start: 0
      Acceptance: 1 Inf(0)
      AP: 1 "a"
      --BODY--
      State: 0
      [0] 0 {0}
      State: 1
      [t] 2 {}
      State: 2
      [!0] 3 {0}
      [0] 0 {}
      State: 3
      [!0] 3 {}
      [0] 0 {}
      --END--""";

  // This example shows that a minimal GFG tNCW cannot drop alpha-edges to states that cannot be
  // entered with the same letter _and_ stay DBP.
  private static final String ONE_LETTER_TEST = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch
      properties: deterministic unambiguous
      properties: complete
      AP: 1 "a"
      --BODY--
      State: 0 "0"
      [!0] 1 {0}
      [0] 2 {}
      State: 1 "2"
      [t] 0 {}
      State: 2 "1"
      [0] 3 {}
      [!0] 4 {0}
      State: 3 "3"
      [0] 3 {}
      [!0] 4 {}
      State: 4 "4"
      [0] 1 {}
      [!0] 2 {}
      --END--
      """;

  private static final String MAXIM_TEST = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch\s
      properties: deterministic unambiguous\s
      properties: complete\s
      AP: 1 "a"
      --BODY--
      State: 0 "0"
      [!0] 1 {}
      [0] 2 {0}
      State: 1 "2"
      [!0] 1 {0}
      [0] 0 {}
      State: 2 "1"
      [!0] 1 {0}
      [0] 2 {}
      --END--
      """;

  private static final String ATTEMPT2_GADGET_MINIMAL_GFG_TNCW = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 0 "bb-self-loop"
      [@a] 3 {}
      [@b] 6 {}
      State: 1 "ba-self-loop"
      [@a] 4 {}
      [@b] 3 {}
      State: 2 "{ab,ba}-self-loop"
      [@a] 5 {}
      [@b] 4 {}
      State: 3 "between 0 and 1"
      [@a] 1 {}
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      State: 4 "between 1 and 2"
      [@a] 2 {}
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      State: 5 "between 2 and 0"
      [@a] 0 {}
      [@b] 2 {}
      State: 6 "companion of 0"
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@b] 0
      --END--
      """;

  private static final String GADGET_MINIMAL_GFG_TNCW = """
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
      [@b] 4 {0}
      [@b] 5 {0}
      [@b] 6 {0}
      [@b] 7 {0}
      [@c] 4
      State: 2 "bc/cb-loop"
      [@a] 3 {0}
      [@a] 4 {0}
      [@a] 5 {0}
      [@a] 6 {0}
      [@a] 7 {0}
      [@b] 7
      [@c] 6
      State: 3 "cc-companion"
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      [@c] 0
      State: 4 "0 -> 1"
      [@a] 1
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      [@c] 0 {0}
      [@c] 1 {0}
      [@c] 2 {0}
      State: 5 "1 -> 0"
      [@a] 0
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      [@c] 1
      State: 6 "0 -> 2"
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@b] 2
      [@c] 0 {0}
      [@c] 1 {0}
      [@c] 2 {0}
      State: 7 "2 -> 0"
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@b] 0
      [@c] 2
      --END--
      """;

  private static final String GADGET_MINIMAL_GFG_TNCW_PRUNED = """
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
      [@c] 4
      State: 2 "bc/cb-loop"
      [@a] 3 {0}
      [@b] 7
      [@c] 6
      State: 3 "cc-companion"
      [@a] 1 {0}
      [@b] 2 {0}
      [@c] 0
      State: 4 "0 -> 1"
      [@a] 1
      [@b] 2 {0}
      [@c] 0 {0}
      [@c] 1 {0}
      State: 5 "1 -> 0"
      [@a] 0
      [@b] 2 {0}
      [@c] 1
      State: 6 "0 -> 2"
      [@a] 1 {0}
      [@b] 2
      [@c] 0 {0}
      [@c] 2 {0}
      State: 7 "2 -> 0"
      [@a] 0 {0}
      [@b] 0
      [@c] 2
      --END--
      """;

  private static final String GADGET_TDCW_ONE_SAFE = """
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
      """;

  private static final String GADGET_TDCW_TWO_SAFE = """
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
      [@b] 9 {0}
      [@c] 8
      State: 2 "bc/cb-loop"
      [@a] 3 {0}
      [@b] 7
      [@c] 6
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
      State: 6 "0 -> 2"
      [@a] 1 {0}
      [@b] 2
      [@c] 1 {0}
      State: 7 "2 -> 0"
      [@a] 0 {0}
      [@b] 0
      [@c] 2
      State: 9 "outsider"
      [@a] 1 {0}
      [@b] 2 {0}
      [@c] 2 {0}
      --END--
      """;

  private static final String GADGET_MINIMAL_TDCW_1 = """
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
      [@c] 6
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
      State: 6 "0 -> 2"
      [@a] 1 {0}
      [@b] 2
      [@c] 1 {0}
      State: 7 "2 -> 0"
      [@a] 0 {0}
      [@b] 0
      [@c] 2
      --END--
      """;

  private static final String GADGET_MINIMAL_TDCW_1_2 = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 0 "bb-self-loop"
      [@a] 3 {}
      [@b] 6 {}
      State: 1 "ba-self-loop"
      [@a] 4 {}
      [@b] 7 {}
      State: 2 "{ab,ba}-self-loop"
      [@a] 5 {}
      [@b] 4 {}
      State: 3 "between 0 and 1 (from 0)"
      [@a] 1 {}
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      State: 4 "between 1 and 2"
      [@a] 2 {}
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      State: 5 "between 2 and 0"
      [@a] 0 {}
      [@b] 2 {}
      State: 6 "companion of 0"
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@b] 0
      State: 7 "between 0 and 1 (from 1)"
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@b] 0
      --END--
      """;

  private static final String NEW_GADGET_MINIMAL_GFG_TNCW = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 0 "bb-loop"
      [@a] 3
      [@b] 7
      State: 1 "ab/ba-loop 1"
      [@a] 4
      [@b] 3
      State: 2 "ab/ba-loop 2"
      [@a] 5
      [@b] 6
      State: 3 "0 -> 1"
      [@a] 1
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      State: 4 "1 -> 2"
      [@a] 2
      [@b] 1
      State: 5 "2 -> 0 via a"
      [@a] 0
      [@b] 2
      State: 6 "2 -> 0 via b"
      [@a] 2
      [@b] 0 {0}
      [@b] 1 {0}
      [@b] 2 {0}
      State: 7 "bb-companion"
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@b] 0
      --END--
      """;

  // the outgoing transitions of 3 and 7 need to match.
  private static final String NEW_GADGET_MINIMAL_TDCW = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 0 "bb-loop"
      [@a] 3
      [@b] 7
      State: 1 "ab/ba-loop 1"
      [@a] 4
      [@b] 8
      State: 2 "ab/ba-loop 2"
      [@a] 5
      [@b] 6
      State: 3 "0 -> 1 (from 0)"
      [@a] 1
      [@b] 1 {0}
      State: 8 "0 -> 1 (from 1)"
      [@a] 1
      [@b] 0 {0}
      State: 4 "1 -> 2"
      [@a] 2
      [@b] 1
      State: 5 "2 -> 0 via a"
      [@a] 0
      [@b] 2
      State: 6 "2 -> 0 via b"
      [@a] 2
      [@b] 0 {0}
      State: 7 "bb-companion"
      [@a] 1 {0}
      [@b] 0
      --END--
      """;

  // the outgoing transitions of 3 and 7 need to match.
  private static final String NEW_GADGET_MINIMAL_TDCW_2 = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 0 "bb-loop"
      [@a] 3
      [@b] 7
      State: 1 "ab/ba-loop 1"
      [@a] 4
      [@b] 8
      State: 2 "ab/ba-loop 2"
      [@a] 5
      [@b] 6
      State: 3 "0 -> 1 (from 0)"
      [@a] 1
      [@b] 2 {0}
      State: 8 "0 -> 1 (from 1)"
      [@a] 1
      [@b] 0 {0}
      State: 4 "1 -> 2"
      [@a] 2
      [@b] 1
      State: 5 "2 -> 0 via a"
      [@a] 0
      [@b] 2
      State: 6 "2 -> 0 via b"
      [@a] 2
      [@b] 0 {0}
      State: 7 "bb-companion"
      [@a] 2 {0}
      [@b] 0
      --END--
      """;


  private static final String TNCW_STRONGLY_EQUIVALENT_DUPLICATE = """
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
      [@b] 2
      State: 1
      [@a] 0
      [@b] 3 {0}
      State: 2
      [t] 1
      State: 3
      [@a] 0 {0}
      [@a] 1 {0}
      [@a] 2 {0}
      [@a] 3 {0}
      [@b] 3
      --END--
      """;

  private static final String TDCW_STRONGLY_EQUIVALENT_DUPLICATE = """
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
      [@b] 2
      State: 1
      [@a] 0
      [@b] 3 {0}
      State: 2
      [t] 1
      State: 3
      [@a] 4 {0}
      [@b] 3
      State: 4
      [@a] 2 {0}
      [@b] 4
      --END--
      """;


  private static final String IS_THERE_A_GAP = """
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
      [0] 3
      [!0] 0 {0}
      [!0] 1 {0}
      [!0] 2 {0}
      [!0] 3 {0}
      [!0] 4 {0}
      State: 1
      [0] 1
      [!0] 0 {0}
      [!0] 1 {0}
      [!0] 2 {0}
      [!0] 3 {0}
      [!0] 4 {0}
      State: 2
      [!0] 3
      [0] 0 {0}
      [0] 1 {0}
      [0] 2 {0}
      [0] 3 {0}
      [0] 4 {0}
      State: 3
      [0] 4
      [!0] 0
      State: 4
      [0] 2
      [!0] 0 {0}
      [!0] 1 {0}
      [!0] 2 {0}
      [!0] 3 {0}
      [!0] 4 {0}
      --END--
      """;

  private static final String IS_THIS_DBP = """
      HOA: v1
      tool: "owl" "21.1-development"
      Start: 0
      acc-name: Buchi
      Acceptance: 1 Inf(0)
      properties: trans-acc no-univ-branch
      properties: deterministic unambiguous
      properties: complete
      AP: 1 "a"
      --BODY--
      State: 0
      [!0] 1 {0}
      [0] 2
      State: 1
      [0] 1
      [!0] 2
      State: 2
      [0] 3
      [!0] 0
      State: 3
      [!0] 4 {0}
      [0] 4
      State: 4
      [!0] 2
      [0] 5
      State: 5
      [!0] 6
      [0] 1 {0}
      State: 6
      [!0] 0 {0}
      [0] 5
      --END--
      """;

  private static final String IS_THIS_GFG_NBW = """
      HOA: v1
      tool: "owl" "21.1-development"
      Start: 0
      acc-name: Buchi
      Acceptance: 1 Inf(0)
      properties: trans-acc no-univ-branch
      properties: deterministic unambiguous
      properties: complete
      AP: 1 "a"
      --BODY--
      State: 0
      [!0] 1 {0}
      [0] 2
      State: 1
      [0] 1
      [!0] 2
      State: 2
      [0] 3
      [!0] 0
      State: 3
      [!0] 4 {0}
      [0] 4
      State: 4
      [!0] 2
      [0] 5
      State: 5
      [!0] 6
      [0] 1 {0}
      State: 6
      [!0] 0 {0}
      [0] 5
      --END--
      """;


  private static final String ELEVATOR_2 = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 0 "ground left"
      [@a] 2
      [@b] 0
      State: 1 "ground right"
      [@b] 0
      State: 2 "level 1"
      [@a] 3
      [@b] 1
      State: 3 "level 2"
      [@b] 2
      --END--
      """;

  private static final String ELEVATOR_3 = """
      HOA: v1
      Start: 1
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 1 "ground right"
      [@a] 2
      State: 2 "level 1"
      [@a] 3
      [@b] 1
      State: 3 "level 2"
      [@a] 4
      [@b] 2
      State: 4 "level 3"
      [@a] 5
      [@b] 3
      State: 5 "level 4"
      [@b] 4
      --END--
      """;

  private static final String ELEVATOR_4 = """
      HOA: v1
      Start: 0
      acc-name: co-Buchi 1
      Acceptance: 1 Fin(0)
      properties: trans-acc no-univ-branch complete
      AP: 1 "a"
      Alias: @a !0
      Alias: @b  0
      --BODY--
      State: 0 "ground left"
      [@a] 2
      [@b] 0
      State: 1 "ground right"
      [@b] 0
      State: 2 "level 1"
      [@a] 3
      [@b] 1
      State: 3 "level 2"
      [@a] 4
      [@b] 2
      State: 4 "level 3"
      [@a] 5
      [@b] 3
      State: 5 "level 4"
      [@b] 4
      --END--
      """;

  private static final String MINIMAL_TDCW_NO_SINGLE_LETTER_PRUNING = """
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
      [@b] 2 {0}
      State: 1
      [@a] 3
      [@b] 4 {0}
      State: 2
      [t]  0
      State: 3
      [@a] 3
      [@b] 4
      State: 4
      [@a] 2
      [@b] 1
      --END--
      """;

  private static final String MINIMAL_TDCW_NO_SINGLE_LETTER_PRUNING_2 = """
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
      [@b] 0 {0}
      State: 1
      [@a] 3
      [@b] 4 {0}
      State: 2
      [t]  0
      State: 3
      [@a] 3
      [@b] 4
      State: 4
      [@a] 2
      [@b] 1
      --END--
      """;

  private final String LONG_STRING = """
      (b &
        (X(a & X a & G (a | Xa)) |
          ((b | Xb) U (Xb & XXa & XXG(a|Xa)))
        )
      )
      """;

  private final String LONG_STRING_1 = """
      (b &
        (X(a & X a & G (a | Xa)) |
          ((b | Xb) U (Xb & XXa & XXG(a|Xa)))
        )
      ) | (
        (a | F (a & Xa)) &
        (F (b & X b & XX(
          (a & Xa & G(a|Xa)) |
          (b|Xb) U (Xb & XXa & XXXa & XXG(a|Xa))
        )))
      )
      """;

  // @Test
  void testMinimalNoPruning() throws ParseException {
    Automaton<Integer, CoBuchiAcceptance> dcw1 = OmegaAcceptanceCast.castExact(
        HoaReader.read(MINIMAL_TDCW_NO_SINGLE_LETTER_PRUNING),
        CoBuchiAcceptance.class);

    Automaton<Integer, CoBuchiAcceptance> dcw2 = OmegaAcceptanceCast.castExact(
        HoaReader.read(MINIMAL_TDCW_NO_SINGLE_LETTER_PRUNING_2),
        CoBuchiAcceptance.class);

    Assertions.assertTrue(LanguageContainment.containsCoBuchi(dcw1, dcw2));
    Assertions.assertTrue(LanguageContainment.containsCoBuchi(dcw2, dcw1));
  }

  // @Test
//  void testElevator2() {
//    for (int level = 2; level < 25; level++) {
//      var elevator = new SearchForGfgExample.GfgNcwElevator(level);
//      var deterministicElevator = new SearchForGfgExample.LinearDcwElevator(level);
//
//      System.out.println("States: " + deterministicElevator.states().size());
//      System.out.println(HoaWriter.toString(deterministicElevator));
//
//      Assertions.assertEquals(
//          GfgNcwMinimization.minimize(
//              deterministicElevator).alphaMaximalUpToHomogenityGfgNcw.states().size(),
//          elevator.states().size());
//
//      Assertions.assertTrue(
//          LanguageContainment.equalsCoBuchi(elevator, deterministicElevator));
//
//      //Assertions.assertEquals(
//      //  Optional.empty(),
//      //  DcwMinimization.minimizeDcw(deterministicElevator));
//
//    }
//  }

  // @Test
//  void testElevator() {
//    for (int level = 2; level < 20; level++) {
//      var elevator = new SearchForGfgExample.GfgNcwElevator(level);
//      var deterministicElevator = Determinization.determinizeCoBuchiAcceptance(elevator);
//
//      Assertions.assertEquals(
//          GfgNcwMinimization.minimize(
//              deterministicElevator).alphaMaximalUpToHomogenityGfgNcw.states().size(),
//          elevator.states().size());
//
//      //Assertions.assertEquals(
//      //  Optional.empty(),
//      //  DcwMinimization.minimizeDcw(deterministicElevator));
//
//      System.out.println(HoaWriter.toString(deterministicElevator));
//    }
//  }

  private static final String FORMULA =
      "(a & XG(a|Xa)) | ((!a & (a -> X !a) U (!a & X t)) | ((a | F(a & Xa)) & F (!a & X!a & XX(t | (a -> X!a) U (!a & Xt)))))";

  @Test
  void testFossacs() throws ParseException {
    Automaton<Integer, CoBuchiAcceptance> fossacsDcw = OmegaAcceptanceCast.castExact(
        HoaReader.read(FOSSACS_EXAMPLE_SIMPLE),
        CoBuchiAcceptance.class);

    Assertions.assertEquals(
        3,
        GfgNcwMinimization.minimize(fossacsDcw).alphaMaximalUpToHomogenityGfgNcw.states()
            .size());

    LabelledFormula fossacsSigma2Formula = LtlParser.parse(
        FORMULA.replace("t", "(a & Xa & XG(a|Xa))"));

    //Automaton<Integer, CoBuchiAcceptance> fossacsFormulaDcw = Views.dropStateLabels(
    //    DeterministicConstructions.CoSafetySafety.of(
    //        fossacsSigma2Formula, true)).automaton();

    // System.err.println(HoaWriter.toString(fossacsFormulaDcw));

//    Assertions.assertEquals(
//        3,
//        GfgNcwMinimization.minimize(fossacsFormulaDcw).alphaMaximalUpToHomogenityGfgNcw.states()
//            .size());
//
//    var minimalDcw1 = DcwMinimizationPruning.minimizeCompleteDcw(
//        fossacsDcw).minimalDcw.orElseThrow();
//    var minimalDcw2 = DcwMinimizationPruning.minimizeCompleteDcw(
//        fossacsFormulaDcw).minimalDcw.orElseThrow();
//
//    Assertions.assertTrue(LanguageContainment.languageEquivalent(minimalDcw1, minimalDcw2));
  }

  @Test
  void testNow() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> minimalGfgTNcw = OmegaAcceptanceCast.cast(
        HoaReader.read(TNCW_STRONGLY_EQUIVALENT_DUPLICATE),
        CoBuchiAcceptance.class);

    Automaton<Integer, ? extends CoBuchiAcceptance> minimalTDcw = OmegaAcceptanceCast.cast(
        HoaReader.read(TDCW_STRONGLY_EQUIVALENT_DUPLICATE),
        CoBuchiAcceptance.class);

    // Assertions.assertTrue(LanguageContainment.equalsCoBuchi(minimalGfgTNcw, minimalTDcw));
    // Assertions.assertEquals(8, minimalGfgTNcw.allRuns().size());
    // Assertions.assertEquals(8, GfgNcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(minimalGfgTNcw)).minimalGfgNcw.allRuns().size());
    // var tDcw = DcwMinimization.minimalDcwForLanguage(minimalGfgTNcw);

//    var tDcw = DcwMinimization.minimalDcwForLanguage(minimalGfgTNcw);
    //  Assertions.assertTrue(minimalGfgTNcw.allRuns().size() < tDcw.get().allRuns().size());
    //System.out.println(HoaWriter.toString(DcwMinimization.minimalDcwForLanguage(minimalGfgTNcw).orElseThrow()));
  }


  @Test
  void testDbp() throws ParseException {
    var minimalTDbw = OmegaAcceptanceCast.cast(
        HoaReader.read(IS_THIS_DBP, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        BuchiAcceptance.class);

    var minimalTGfgNbw = OmegaAcceptanceCast.cast(
        HoaReader.read(IS_THIS_GFG_NBW, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        BuchiAcceptance.class);

    Assertions.assertTrue(LanguageContainment.languageEquivalent(minimalTDbw,
        NbaDet.determinize(minimalTGfgNbw, new AutomatonConversionCommands.Nba2DpaCommand(null))));
  }

  @Test
  void testGap() throws ParseException {
    var minimalGfgTNcw = OmegaAcceptanceCast.cast(
        HoaReader.read(IS_THERE_A_GAP, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    var tDcw = Determinization.determinizeCoBuchiAcceptance(minimalGfgTNcw);

    Assertions.assertEquals(
        GfgNcwMinimization.minimize(tDcw).alphaMaximalUpToHomogenityGfgNcw.states().size(),
        minimalGfgTNcw.states().size());
    var minimalTDcw = DcwMinimization.minimize(tDcw);
    System.out.println(HoaWriter.toString(minimalTDcw));
  }

  @Test
  void testMinimial2() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> minimalGfgTNcw = OmegaAcceptanceCast.cast(
        HoaReader.read(NEW_GADGET_MINIMAL_GFG_TNCW,
            FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    Automaton<Integer, ? extends CoBuchiAcceptance> minimalTDcw = OmegaAcceptanceCast.cast(
        HoaReader.read(NEW_GADGET_MINIMAL_TDCW, FactorySupplier.defaultSupplier()::getBddSetFactory,
            null),
        CoBuchiAcceptance.class);

    Automaton<Integer, ? extends CoBuchiAcceptance> minimalTDcw2 = OmegaAcceptanceCast.cast(
        HoaReader.read(NEW_GADGET_MINIMAL_TDCW_2,
            FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(minimalGfgTNcw, minimalTDcw));
    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(minimalTDcw, minimalTDcw2));
    Assertions.assertEquals(8, minimalGfgTNcw.states().size());
    Assertions.assertEquals(8, GfgNcwMinimization.minimize(
        Determinization.determinizeCoBuchiAcceptance(
            minimalGfgTNcw)).alphaMaximalUpToHomogenityGfgNcw.states().size());
    var tDcw = DcwMinimization.minimize(minimalGfgTNcw);

//    var tDcw = DcwMinimization.minimalDcwForLanguage(minimalGfgTNcw);
    //  Assertions.assertTrue(minimalGfgTNcw.allRuns().size() < tDcw.get().allRuns().size());
    //System.out.println(HoaWriter.toString(DcwMinimization.minimalDcwForLanguage(minimalGfgTNcw).orElseThrow()));
  }


  @Test
  void testMinimial() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> minimalGfgTNcw = OmegaAcceptanceCast.cast(
        HoaReader.read(GADGET_MINIMAL_GFG_TNCW, FactorySupplier.defaultSupplier()::getBddSetFactory,
            null),
        CoBuchiAcceptance.class);

    Automaton<Integer, ? extends CoBuchiAcceptance> minimalGfgTNcwPruned = OmegaAcceptanceCast.cast(
        HoaReader.read(GADGET_MINIMAL_GFG_TNCW_PRUNED,
            FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    Automaton<Integer, ? extends CoBuchiAcceptance> tDcw = OmegaAcceptanceCast.cast(
        HoaReader.read(GADGET_TDCW_ONE_SAFE, FactorySupplier.defaultSupplier()::getBddSetFactory,
            null),
        CoBuchiAcceptance.class);

    Automaton<Integer, ? extends CoBuchiAcceptance> tDcw2 = OmegaAcceptanceCast.cast(
        HoaReader.read(GADGET_TDCW_TWO_SAFE, FactorySupplier.defaultSupplier()::getBddSetFactory,
            null),
        CoBuchiAcceptance.class);

    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(minimalGfgTNcw, minimalGfgTNcwPruned));
    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(minimalGfgTNcw, tDcw));
    // Assertions.assertTrue(LanguageContainment.equalsCoBuchi(tDcw, tDcw2));
    Assertions.assertEquals(8, minimalGfgTNcw.states().size());
    Assertions.assertEquals(8, minimalGfgTNcwPruned.states().size());
    Assertions.assertEquals(8, GfgNcwMinimization.minimize(
        Determinization.determinizeCoBuchiAcceptance(
            minimalGfgTNcwPruned)).alphaMaximalUpToHomogenityGfgNcw.states().size());

    //  var tDcw = DcwMinimization.minimalDcwForLanguage(minimalGfgTNcw);
    //  Assertions.assertTrue(minimalGfgTNcw.allRuns().size() < tDcw.get().allRuns().size());
    //System.out.println(HoaWriter.toString(DcwMinimization.minimalDcwForLanguage(minimalGfgTNcw).orElseThrow()));
  }

  private static Automaton<Integer, CoBuchiAcceptance> addMissingAlphaTransitions(
      Automaton<Integer, ? extends CoBuchiAcceptance> ncw) {

    Set<Edge<Integer>> alphaEdges
        = ncw.states().stream().map(x -> Edge.of(x, 0)).collect(Collectors.toUnmodifiableSet());

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        ncw.atomicPropositions(), ncw.factory(), ncw.initialStates(), CoBuchiAcceptance.INSTANCE) {

      @Override
      protected MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
        return ncw.edgeTree(state).map(x -> x.isEmpty() ? alphaEdges : x);
      }
    };
  }

  @Test
  void testThreeComponents() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = addMissingAlphaTransitions(
        OmegaAcceptanceCast.cast(
            HoaReader.read(THREE_COMPONENTS, FactorySupplier.defaultSupplier()::getBddSetFactory,
                null),
            CoBuchiAcceptance.class));

    System.out.println(HoaWriter.toString(ncw1));
    System.out.println(
        HoaWriter.toString(DcwMinimization.minimize(ncw1)));
  }

  @Test
  void testMaximTest() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = OmegaAcceptanceCast.cast(
        HoaReader.read(MAXIM_TEST, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(
        HoaWriter.toString(DcwMinimization.minimize(ncw1)));
  }

  @Test
  void testRR4AP1() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = OmegaAcceptanceCast.cast(
        HoaReader.read(RR_Q4_AP1, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(HoaWriter.toString(ncw1));
    System.out.println(
        HoaWriter.toString(DcwMinimization.minimize(ncw1)));
  }

  // @Test
  void testRandAut() throws Exception {
    var automata = HoaReader.readMultiple(Path.of(
            "/Users/bob/Teleporter/Fedora Workstation 35/docker/data3/minimal-tDCW_Q20_AP1.hoa"),
        FactorySupplier.defaultSupplier()::getBddSetFactory, null);

    for (var automaton : automata) {
      var minimalDcw
          = DcwMinimization.minimize(
          OmegaAcceptanceCast.cast(automaton, CoBuchiAcceptance.class));

      // if (minimalDcw.isEmpty()) {
//        System.err.println(HoaWriter.toString(automaton));
//        System.err.println(HoaWriter.toString(GfgNcwMinimization.minimize(
//            OmegaAcceptanceCast.castExact(automaton,
//                CoBuchiAcceptance.class)).alphaMaximalUpToHomogenityGfgNcw));
//      }
    }
  }

  @Test
  void testThreeComponents2() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = addMissingAlphaTransitions(
        OmegaAcceptanceCast.cast(
            HoaReader.read(THREE_COMPONENTS_2, FactorySupplier.defaultSupplier()::getBddSetFactory,
                null),
            CoBuchiAcceptance.class));

    System.out.println(HoaWriter.toString(ncw1));
    System.out.println(
        HoaWriter.toString(DcwMinimization.minimize(ncw1)));
  }

  @Test
  void minimizeElevator() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = OmegaAcceptanceCast.cast(
        HoaReader.read(ELEVATOR, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(
        HoaWriter.toString(DcwMinimization.minimize(ncw1)));
    System.out.println(HoaWriter.toString(Determinization.determinizeCanonicalGfgNcw(
        GfgNcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(ncw1)))));
  }

  @Test
  void minimize() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = OmegaAcceptanceCast.cast(
        HoaReader.read(EXAMPLE_1, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(HoaWriter.toString(
        DcwMinimization.minimize(ncw1)));

    Automaton<Integer, ? extends CoBuchiAcceptance> ncw2 = OmegaAcceptanceCast.cast(
        HoaReader.read(EXAMPLE_2, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(HoaWriter.toString(
        DcwMinimization.minimize(
            Determinization.determinizeCoBuchiAcceptance(ncw2))));

    Automaton<?, ? extends CoBuchiAcceptance> ncw3 =
        GfgNcwMinimization.minimize(
            Determinization.determinizeCoBuchiAcceptance(ncw2)).alphaMaximalUpToHomogenityGfgNcw;

    System.out.println(HoaWriter.toString(
        DcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(ncw3))));
  }

  @Test
  void minimizeOferExample() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = OmegaAcceptanceCast.cast(
        HoaReader.read(HOA_GFG_CO_BUCHI, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(HoaWriter.toString(
        DcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(ncw1))));

    var dcw2 = Determinization.determinizeCanonicalGfgNcw(
        GfgNcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(ncw1)));

    System.out.println(HoaWriter.toString(dcw2));
    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(ncw1, dcw2));
  }

  static Stream<Pair<DcwMinimisationTestCase, Integer>> dcwMinimisationTestCaseProvider() {
    return Stream.concat(
            IntStream.rangeClosed(2, 3).mapToObj(DcwRepository::permutationLanguageTestCase),
            DcwRepository.GFG_NCW_HELPFUL_EXAMPLES.stream())
        .flatMap(testCase -> IntStream
            .rangeClosed(
                testCase.canonicalGfgNcw().alphaMaximalGfgNcw.states().size() - 2,
                testCase.minimalDcwSize() + 4)
            .mapToObj(i -> Pair.of(testCase, i)));
  }

  static Stream<Pair<DcwMinimisationTestCase, Integer>> dcwMinimisationTestCaseProviderDb()
      throws IOException, ParseException {
    return DcwRepository.fromDb().stream()
        .flatMap(testCase -> IntStream
            .rangeClosed(testCase.minimalDcwSize(), testCase.minimalDcwSize() + 3)
            .mapToObj(i -> Pair.of(testCase, i)));
  }

  // fromDb

  @ParameterizedTest
  @MethodSource("dcwMinimisationTestCaseProvider")
  void verifyTestcasesUsingReferenceImplementation(Pair<DcwMinimisationTestCase, Integer> pair) {
    DcwMinimisationTestCase testCase = pair.fst();
    int i = pair.snd();

    var dcw = DcwMinimizationReferenceImplementation.minimizeCompleteDcw(testCase.canonicalGfgNcw(),
        i, false);

    if (i < testCase.minimalDcwSize()) {
      Assertions.assertEquals(Optional.empty(), dcw);
    } else {
      Assertions.assertTrue(dcw.isPresent());
      Assertions.assertTrue(LanguageContainment.equalsCoBuchi(
          testCase.canonicalGfgNcw().alphaMaximalUpToHomogenityGfgNcw, dcw.get()));
    }
  }

  @ParameterizedTest
  @MethodSource("dcwMinimisationTestCaseProvider")
  void testDcwMinimisationThm8(Pair<DcwMinimisationTestCase, Integer> pair) {
    DcwMinimisationTestCase testCase = pair.fst();
    int i = pair.snd();

    var dcw = DcwMinimizationForGfgLanguages.guess(testCase.canonicalGfgNcw(), i);

    if (i < testCase.minimalDcwSize()) {
      Assertions.assertEquals(Optional.empty(), dcw);
    } else {
      Assertions.assertTrue(dcw.isPresent());
      Assertions.assertTrue(LanguageContainment.equalsCoBuchi(
          testCase.canonicalGfgNcw().alphaMaximalUpToHomogenityGfgNcw, dcw.get()));
    }
  }

  @ParameterizedTest
  @MethodSource("dcwMinimisationTestCaseProvider")
  void testDcwMinimisation(Pair<DcwMinimisationTestCase, Integer> pair) {
    DcwMinimisationTestCase testCase = pair.fst();

    var dcw = DcwMinimization.minimize(testCase.canonicalGfgNcw().dcw);

    Assertions.assertEquals(testCase.minimalDcwSize(), dcw.states().size());
    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(testCase.canonicalGfgNcw().dcw, dcw));
  }

  // @Test
  void search() throws IOException, ParseException, InterruptedException {
    search(DcwRepository.fromDb().stream().map(DcwMinimisationTestCase::canonicalGfgNcw));
  }

  // @Test
  void searchRandom() throws IOException, InterruptedException {
    search(
        DcwRepository.randomAutomataStream(new SplittableRandom(System.currentTimeMillis()), 1, 3,
                3)
            .map(
                x -> GfgNcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(x))));
  }


  void search(Stream<CanonicalGfgNcw> stream) throws IOException, InterruptedException {
    BlockingQueue<Pair<CanonicalGfgNcw, Automaton<Integer, CoBuchiAcceptance>>> blockingQueue = new ArrayBlockingQueue<>(
        100);

    int elements = 0;

    // Off-load computation to different thread.
    new Thread(() -> stream.unordered().parallel().mapMulti(
        (CanonicalGfgNcw canonicalGfgNcw, Consumer<Pair<CanonicalGfgNcw, Automaton<Integer, CoBuchiAcceptance>>> consumer) -> {

          if (DcwMinimization.minimize(
              Determinization.determinizeCanonicalGfgNcw(canonicalGfgNcw)).states().size() >= 1) {
            return;
          }

          Automaton<Integer, CoBuchiAcceptance> minimalDcw = null;

          int canonicalGfgNcwSize = canonicalGfgNcw.alphaMaximalGfgNcw.states().size();
          int minimalDcwSize = canonicalGfgNcwSize + 1;

          while (true) {
            minimalDcw = DcwMinimizationForGfgLanguages.guess(canonicalGfgNcw,
                    minimalDcwSize)
                .orElse(null);

            if (minimalDcw != null) {
              break;
            }

            System.out.println("Gap: " + (minimalDcwSize - canonicalGfgNcwSize));

            if (minimalDcwSize - canonicalGfgNcwSize > 5) {
              System.out.println(
                  HoaWriter.toString(canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw));
            }

            minimalDcwSize++;
          }

          Assertions.assertTrue(LanguageContainment.equalsCoBuchi(
              canonicalGfgNcw.alphaMaximalGfgNcw, minimalDcw));
          Assertions.assertEquals(minimalDcwSize, minimalDcw.states().size());

          if (minimalDcwSize != canonicalGfgNcwSize) {
            consumer.accept(Pair.of(canonicalGfgNcw, minimalDcw));
          }
        }).limit(100).forEach(blockingQueue::add)).start();

    while (elements < 100) {
      Pair<CanonicalGfgNcw, Automaton<Integer, CoBuchiAcceptance>> pair = blockingQueue.take();

      CanonicalGfgNcw canonicalGfgNcw = pair.fst();
      int minimalDcwSize = pair.snd().states().size();

      System.out.printf("Got an element with gap: %d%n",
          minimalDcwSize - canonicalGfgNcw.alphaMaximalGfgNcw.states().size());
      System.out.flush();

      Files.write(Path.of("gfg-helpful-23.hoa"), List.of(HoaWriter.toString(pair.snd()), "\n"),
          StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

      Automaton<Integer, CoBuchiAcceptance> fixedSafeMinimalDcw = null;

      if (true || false) {
        continue;
      }

      for (int i = 0; i < 10 && fixedSafeMinimalDcw == null; i++) {
        fixedSafeMinimalDcw = DcwMinimizationReferenceImplementation.minimizeCompleteDcw(
                canonicalGfgNcw,
                minimalDcwSize + i,
                true)
            .orElse(null);

        if (fixedSafeMinimalDcw != null && i > 8) {
          System.err.println("DCW (more safecomponents)");
          System.err.println(HoaWriter.toString(pair.snd()));
          System.err.println("DCW (same safecomponents)");
          System.err.println(HoaWriter.toString(fixedSafeMinimalDcw));
        }
      }

      elements++;
    }

//      {
//        var test = Determinization.determinizeCanonicalGfgNcw2(testCase.canonicalGfgNcw());
//
//        int safeComponents = SccDecomposition.of(
//                test.states(),
//                state -> test.edges(state).stream()
//                    .filter(x -> x.colours().isEmpty())
//                    .map(Edge::successor)
//                    .toList())
//            .sccs()
//            .size();
//
//        if (canonicalGfgNcw.safeComponents.size() < safeComponents) {
//          System.err.println("DCW (more safecomponents)");
//          System.err.println(HoaWriter.toString(test));
//          System.err.println(canonicalGfgNcw.subsafeEquivalentRelation);
//        }
//
//        Assertions.assertEquals(canonicalGfgNcw.safeComponents.size(), safeComponents);
//      }
  }


  @Test
  void minimizeOferExample2Dcw() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = OmegaAcceptanceCast.cast(
        HoaReader.read(HOA_GFG_CO_BUCHI, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(HoaWriter.toString(
        DcwMinimization.minimize(Determinization.determinizeCoBuchiAcceptance(ncw1))));

    var dcw1 = DcwMinimizationReferenceImplementation.minimalDcwForLanguage(ncw1, 1);
    var dcw2 = DcwMinimizationReferenceImplementation.minimalDcwForLanguage(ncw1, 2);
    var dcw3 = DcwMinimizationReferenceImplementation.minimalDcwForLanguage(ncw1, 3);
    var dcw4 = DcwMinimizationReferenceImplementation.minimalDcwForLanguage(ncw1, 4);
    var dcw5 = DcwMinimizationReferenceImplementation.minimalDcwForLanguage(ncw1, 5);

    Assertions.assertEquals(Optional.empty(), dcw1);
    Assertions.assertEquals(Optional.empty(), dcw2);
    Assertions.assertEquals(Optional.empty(), dcw3);
    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(ncw1, dcw4.get()));
    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(ncw1, dcw5.get()));
  }

  @Test
  void minimizeDbpAlphaMaximalExample2() throws ParseException {
    Automaton<Integer, ? extends CoBuchiAcceptance> ncw1 = OmegaAcceptanceCast.cast(
        HoaReader.read(NEXT_EXAMPLE, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    Automaton<Integer, ? extends CoBuchiAcceptance> ncw2 = OmegaAcceptanceCast.cast(
        HoaReader.read(NEXT_EXAMPLE_2, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
        CoBuchiAcceptance.class);

    System.out.println(HoaWriter.toString(GfgNcwMinimization.minimize(
        Determinization.determinizeCoBuchiAcceptance(ncw1)).alphaMaximalUpToHomogenityGfgNcw));
    System.out.println(HoaWriter.toString(GfgNcwMinimization.minimize(
        Determinization.determinizeCoBuchiAcceptance(ncw2)).alphaMaximalUpToHomogenityGfgNcw));
    System.out.println(
        HoaWriter.toString(DcwMinimization.minimize(ncw1)));
  }

  // graphPermutationLanguage2

  @Test
  void minimizeGfgExampleFail() {
    Automaton<?, ? extends CoBuchiAcceptance> ncw1 =
        GfgNcwMinimization.minimize(
            Views.dropStateLabels(Determinization.determinizeCoBuchiAcceptance(
                    DcwRepository.permutationLanguage(3)))
                .automaton()).alphaMaximalUpToHomogenityGfgNcw;

    Assertions.assertEquals(6, DcwMinimization.minimize(ncw1).states().size());
  }

  @Test
  void testLtl() {
    var formula = LtlParser.parse("(FG(a <-> XXXa)) | FGb | FG (c <-> Xc)").nnf();
    var dcw1 = DeterministicConstructions.CoSafetySafety.of(formula);
    var dcw2 = Determinization.determinizeCanonicalGfgNcw(GfgNcwMinimization.minimize(dcw1));

    System.out.println(HoaWriter.toString(dcw1));
    System.out.println(HoaWriter.toString(dcw2));
    Assertions.assertTrue(LanguageContainment.equalsCoBuchi(dcw1, dcw2));
  }
}
