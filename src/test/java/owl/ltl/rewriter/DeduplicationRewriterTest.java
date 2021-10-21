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

package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class DeduplicationRewriterTest {
  static List<Formula> formulas() {
    return Stream.of(
      "F a & G F a & X X F a & X F a & a U F a & b U F a & F (c & F b | F a)",
      "G a & F b & a M b & a R b & G b | F b & a U b | a => b | a <=> b | b <=> a | X a | X b",
      "a W b & b W a & a M b & a M b & b M a & a U b | a U b & a M b | a U b | a U c",
      "X X X X X X a & X X X X X a & X X X X X a & X X X X X b & a U X X X b | b"
    ).map(input -> LtlParser.parse(input).formula()).toList();
  }

  @ParameterizedTest
  @MethodSource("formulas")
  public void test(Formula formula) {
    Formula deduplicate = DeduplicationRewriter.deduplicate(formula);

    assertEquals(formula, deduplicate,
      "Deduplicated formula does not equal original:\n" + formula + "\n" + deduplicate);
  }
}
