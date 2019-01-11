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

package owl.ltl.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.HOperator;
import owl.ltl.Literal;
import owl.ltl.OOperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.YOperator;

class SpectraParserTest {

  //<editor-fold desc="Test inputs">
  private static final List<String> SyntaxTestIn = List.of(
    //<editor-fold desc="Test 0: false, [env-initial]">
    "asm false;",
    //</editor-fold>
    //<editor-fold desc="Test 1: true, [sys-initial]">
    "gar true;",
    //</editor-fold>
    //<editor-fold desc="Test 2: variable, [env-safety]">
    "env boolean a;\n"
    + "asm G a;",
    //</editor-fold>
    //<editor-fold desc="Test 3: negated variable, [env-liveness]">
    "env boolean a;\n"
    + "asm GF !a;",
    //</editor-fold>
    //<editor-fold desc="Test 4: implication, [env-safety]">
    "env boolean a;\n"
    + "asm G a -> a;",
    //</editor-fold>
    //<editor-fold desc="Test 5: implicate negation, [env-safety]">
    "env boolean a;\n"
    + "asm G a -> !a;",
    //</editor-fold>
    //<editor-fold desc="Test 6: and, [sys-liveness]">
    "env boolean a;\n"
    + "sys boolean b;\n"
    + "gar GF a & b;",
    //</editor-fold>
    //<editor-fold desc="Test 7: or, [sys-liveness]">
    "env boolean a;\n"
    + "sys boolean b;\n"
    + "gar GF a | b;",
    //</editor-fold>
    //<editor-fold desc="Test 8: biconditional, [sys-safety]">
    "env boolean a;\n"
    + "sys boolean b;\n"
    + "gar G a <-> b;",
    //</editor-fold>
    //<editor-fold desc="Test 9: next-operator, [env-safety]">
    "env boolean a;\n"
    + "asm G next(a);",
    //</editor-fold>
    //<editor-fold desc="Test 10: Once-operator, [env-safety]">
    "env boolean a;\n"
    + "asm G O(a);",
    //</editor-fold>
    //<editor-fold desc="Test 11: Historically-operator, [env-safety]">
    "env boolean a;\n"
    + "asm G H(a);",
    //</editor-fold>
    //<editor-fold desc="Test 12: Yesterday-operator, [env-safety]">
    "env boolean a;\n"
    + "asm G Y(a);",
    //</editor-fold>
    //<editor-fold desc="Test 13: Since-operator, [env-safety]">
    "env boolean a;\n"
    + "env boolean b;\n"
    + "asm G a S b;",
    //</editor-fold>
    //<editor-fold desc="Test 14: Triggered-operator, [env-safety]">
    "env boolean a;\n"
    + "env boolean b;\n"
    + "asm G a T b;",
    //</editor-fold>
    //<editor-fold desc="Test 15: variable = false, [env-livness]">
    "env boolean a;\n"
    + "asm GF a = false;",
    //</editor-fold>
    //<editor-fold desc="Test 16: variable != true, [sys-livness]">
    "sys boolean a;\n"
    + "gar GF a != true;",
    //</editor-fold>
    //<editor-fold desc="Test 17: var of IntRange = const, [env-initial]">
    "env Int(0..3) number;\n"
    + "asm number = 2;",
    //</editor-fold>
    //<editor-fold desc="Test 18: var of IntRange < const, [sys-initial]">
    "sys Int(0..3) number;\n"
    + "gar number < 2;",
    //</editor-fold>
    //<editor-fold desc="Test 19: var of IntRange <= const, [env-safety]">
    "env Int(0..3) number;\n"
    + "asm G number <= 3;",
    //</editor-fold>
    //<editor-fold desc="Test 20: var of IntRange > const, [sys-safety]">
    "sys Int(0..3) number;\n"
    + "gar G number > 1;",
    //</editor-fold>
    //<editor-fold desc="Test 21: type foo of IntRange, var a,b of foo, a >= b, [sys-liveness]">
    "type foo = Int(0..3);\n"
    + "env foo a;\n"
    + "sys foo b;\n"
    + "gar GF a >= b;",
    //</editor-fold>
    //<editor-fold desc="Test 22: type of type, array, [sys-liveness]">
    "type foo = Int(0..3);\n"
    + "type bar = foo[2];\n"
    + "sys bar a;\n"
    + "gar a[0] != 0 & a[1] = 2;",
    //</editor-fold>
    //<editor-fold desc="Test 23: unordered test 22, [sys-liveness]">
    "type bar = foo[2];\n"
    + "gar a[0] != 0 & a[1] = 2;\n"
    + "sys bar a;\n"
    + "type foo = Int(0..3);"
  //</editor-fold>
  );

  private static final String NonWellSepIn =
    "module NonWellSep\n"
      + "env boolean atStation;\n"
      + "env boolean cargo;\n"
      + "sys {STOP, FWD, BWD} mot;\n"
      + "sys {LIFT, DROP} lift;\n"
      + "asm findStat: // always possible to find a station\n"
      + "GF (atStation);\n"
      + "asm samePos: // same station position when stopped\n"
      + "G (mot=STOP -> next(atStation)=atStation);\n"
      + "asm​ liftCargo: ​// lifting clears sensor​\n"
      + "G (lift=​LIFT​ -> ​next​(!cargo));\n"
      + "asm​ dropCargo: ​// dropping senses cargo\n"
      + "​G​ (lift=​DROP​ -> ​next​(cargo));\n"
      + "asm​ clearCargo: ​// backing up clears cargo\n"
      + "​G​ (mot=​BWD​ -> ​next​(!cargo));"
    ;
  //</editor-fold>

  //<editor-fold desc="Test outputs">
  /**
   * - Dimension 0: test[i] for i \in [0,dim0.length]
   * - Dimension 1:
   *    - test[i][0]: environment-initial
   *    - test[i][1]: system-initial
   *    - test[i][2]: environment-safety
   *    - test[i][3]: system-safety
   *    - test[i][4]: environment-liveness
   *    - test[i][5]: system-liveness
   * - Dimension 2: list of ltl/pltl2safety formulas
   */
  private static final List<Formula[][]> SyntaxTestOut = List.of(new Formula[][][]{
    //<editor-fold desc="Test 0">
    {
      {BooleanConstant.FALSE},
      {},
      {},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 1">
    {
      {},
      {BooleanConstant.TRUE},
      {},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 2">
    {
      {},
      {},
      {Literal.of(0)},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 3">
    {
      {},
      {},
      {},
      {},
      {new GOperator(
        new FOperator(Literal.of(0).not())
      )},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 4">
    {
      {},
      {},
      {Disjunction.of(Literal.of(0).not(), Literal.of(0))},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 5">
    {
      {},
      {},
      {Disjunction.of(Literal.of(0).not(), Literal.of(0).not())},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 6">
    {
      {},
      {},
      {},
      {},
      {},
      {new GOperator(
        new FOperator(Conjunction.of(Literal.of(0), Literal.of(1)))
      )}
    },
    //</editor-fold>
    //<editor-fold desc="Test 7">
    {
      {},
      {},
      {},
      {},
      {},
      {new GOperator(
        new FOperator(Disjunction.of(Literal.of(0), Literal.of(1)))
      )}
    },
    //</editor-fold>
    //<editor-fold desc="Test 8">
    {
      {},
      {},
      {},
      {Biconditional.of(Literal.of(0), Literal.of(1))},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 9">
    {
      {},
      {},
      {new XOperator(Literal.of(0))},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 10">
    {
      {},
      {},
      {new OOperator(Literal.of(0))},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 11">
    {
      {},
      {},
      {new HOperator(Literal.of(0))},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 12">
    {
      {},
      {},
      {new YOperator(Literal.of(0))},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 13">
    {
      {},
      {},
      {new SOperator(Literal.of(0), Literal.of(1))},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 14">
    {
      {},
      {},
      {new TOperator(Literal.of(0), Literal.of(1))},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 15">
    {
      {},
      {},
      {},
      {},
      {new GOperator(
        new FOperator(Biconditional.of(Literal.of(0), BooleanConstant.FALSE))
      )},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 16">
    {
      {},
      {},
      {},
      {},
      {},
      {new GOperator(
        new FOperator(Biconditional.of(Literal.of(0), BooleanConstant.TRUE).not())
      )}
    },
    //</editor-fold>
    //<editor-fold desc="Test 17">
    {
      {Conjunction.of(
        Biconditional.of(Literal.of(0), BooleanConstant.FALSE),
        Biconditional.of(Literal.of(1), BooleanConstant.TRUE)
      )},
      {},
      {},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 18">
    {
      {},
      {Disjunction.of(
        Conjunction.of(Literal.of(1).not(), BooleanConstant.TRUE),
        Conjunction.of(
          Biconditional.of(Literal.of(1), BooleanConstant.TRUE),
          Disjunction.of(
            Conjunction.of(Literal.of(0).not(), BooleanConstant.FALSE),
            Conjunction.of(
              Biconditional.of(Literal.of(0), BooleanConstant.FALSE),
              BooleanConstant.FALSE
            )
          )
        )
      )},
      {},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 19">
    {
      {},
      {},
      {Disjunction.of(
        Conjunction.of(Literal.of(1).not(), BooleanConstant.TRUE),
        Conjunction.of(
          Biconditional.of(Literal.of(1), BooleanConstant.TRUE),
          Disjunction.of(
            Conjunction.of(Literal.of(0).not(), BooleanConstant.TRUE),
            Conjunction.of(
              Biconditional.of(Literal.of(0), BooleanConstant.TRUE),
              BooleanConstant.TRUE
            )
          )
        )
      )},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 20">
    {
      {},
      {},
      {},
      {Disjunction.of(
        Conjunction.of(BooleanConstant.FALSE.not(), Literal.of(1)),
        Conjunction.of(
          Biconditional.of(BooleanConstant.FALSE, Literal.of(1)),
          Disjunction.of(
            Conjunction.of(BooleanConstant.TRUE.not(), Literal.of(0)),
            Conjunction.of(
              Biconditional.of(BooleanConstant.TRUE, Literal.of(0)),
              BooleanConstant.FALSE
            )
          )
        )
      )},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 21">
    {
      {},
      {},
      {},
      {},
      {},
      {new GOperator(
        new FOperator(
          Disjunction.of(
            Conjunction.of(Literal.of(3).not(), Literal.of(1)),
            Conjunction.of(
              Biconditional.of(Literal.of(3), Literal.of(1)),
              Disjunction.of(
                Conjunction.of(Literal.of(2).not(), Literal.of(0)),
                Conjunction.of(
                  Biconditional.of(Literal.of(2), Literal.of(0)),
                  BooleanConstant.TRUE
                )
              )
            )
          )
        )
      )}
    },
    //</editor-fold>
    //<editor-fold desc="Test 22">
    {
      {},
      {Conjunction.of(
        Disjunction.of(
          Biconditional.of(
            Literal.of(0), BooleanConstant.FALSE
          ).not(),
          Biconditional.of(
            Literal.of(1), BooleanConstant.FALSE
          ).not()
        ),
        Conjunction.of(
          Biconditional.of(
            Literal.of(2), BooleanConstant.FALSE
          ),
          Biconditional.of(
            Literal.of(3), BooleanConstant.TRUE
          )
        )
      )},
      {},
      {},
      {},
      {}
    },
    //</editor-fold>
    //<editor-fold desc="Test 23">
    {
      {},
      {Conjunction.of(
        Disjunction.of(
          Biconditional.of(
            Literal.of(0), BooleanConstant.FALSE
          ).not(),
          Biconditional.of(
            Literal.of(1), BooleanConstant.FALSE
          ).not()
        ),
        Conjunction.of(
          Biconditional.of(
            Literal.of(2), BooleanConstant.FALSE
          ),
          Biconditional.of(
            Literal.of(3), BooleanConstant.TRUE
          )
        )
      )},
      {},
      {},
      {},
      {}
    },
    //</editor-fold>
  });

  private static final Formula NonWellSepOut = nonWellSepOut();
  //</editor-fold>

  //<editor-fold desc="Test output generating methods">
  private static Formula nonWellSepOut() {
    Literal atStation = Literal.of(0);
    Literal cargo = Literal.of(1);
    Literal[] motVar = {Literal.of(2), Literal.of(3)};
    Literal liftVar = Literal.of(4);

    Formula findStation = new GOperator(new FOperator(atStation));

    Formula samePosLeft = Conjunction.of(
      Biconditional.of(motVar[0], BooleanConstant.FALSE),
      Biconditional.of(motVar[1], BooleanConstant.FALSE)
    );
    Formula samePosRight = Conjunction.of(
      Biconditional.of(new XOperator(atStation), atStation)
    );
    Formula samePos = Disjunction.of(samePosLeft.not(), samePosRight);

    Formula liftCargoLeft = Conjunction.of(
      Biconditional.of(liftVar, BooleanConstant.FALSE)
    );
    Formula liftCargoRight = new XOperator(cargo.not());
    Formula liftCargo = Disjunction.of(liftCargoLeft.not(), liftCargoRight);

    Formula dropCargoLeft = Conjunction.of(
      Biconditional.of(liftVar, BooleanConstant.TRUE)
    );
    Formula dropCargoRight = new XOperator(cargo);
    Formula dropCargo = Disjunction.of(dropCargoLeft.not(), dropCargoRight);

    Formula clearCargoLeft = Conjunction.of(
      Biconditional.of(motVar[0], BooleanConstant.FALSE),
      Biconditional.of(motVar[1], BooleanConstant.TRUE)
    );
    Formula clearCargoRight = new XOperator(cargo.not());
    Formula clearCargo = Disjunction.of(clearCargoLeft.not(), clearCargoRight);

    Formula[][] test = new Formula[][] {
      {},
      {},
      {samePos, liftCargo, dropCargo, clearCargo},
      {},
      {findStation},
      {}
    };

    return strictRealizable(test);
  }

  private static Formula strictRealizable(Formula[][] part) {
    Formula initialE = Conjunction.of(part[0]);
    Formula initialS = Conjunction.of(part[1]);
    Formula safetyE = Conjunction.of(part[2]);
    Formula safetyS = Conjunction.of(part[3]);
    Formula livenessE = Conjunction.of(part[4]);
    Formula livenessS = Conjunction.of(part[5]);

    Formula part1 = Disjunction.of(livenessE.not(), livenessS);
    Formula part2 = Conjunction.of(new GOperator(safetyE), part1);
    Formula part3 = WOperator.of(safetyS, safetyE.not());
    Formula part4 = Conjunction.of(initialS, part3, part2);

    return Disjunction.of(initialE.not(), part4);
  }
  //</editor-fold>

  //<editor-fold desc="Tests">
  @Test
  void testSyntax() {
    for (int i = 0; i < SyntaxTestIn.size(); i++) {
      assertEquals(strictRealizable(SyntaxTestOut.get(i)),
        SpectraParser.parse(SyntaxTestIn.get(i)).toFormula());
    }
  }

  @Test
  void testNonWellSep() {
    assertEquals(NonWellSepOut, SpectraParser.parse(NonWellSepIn).toFormula());
  }
  //</editor-fold>
}