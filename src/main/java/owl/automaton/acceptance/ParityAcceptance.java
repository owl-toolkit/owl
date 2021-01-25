/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;
import static owl.logic.propositional.PropositionalFormula.constant;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnegative;
import owl.automaton.edge.Edge;
import owl.logic.propositional.PropositionalFormula;

public final class ParityAcceptance extends OmegaAcceptance {
  @Nonnegative
  private final int colours;
  private final Parity parity;

  public ParityAcceptance(@Nonnegative int colours, Parity parity) {
    this.colours = colours;
    this.parity = parity;
  }

  @Override
  public String name() {
    return "parity";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of(parity.maxString(), parity.evenString(), colours);
  }

  @Override
  public Optional<BitSet> acceptingSet() {
    BitSet set = new BitSet();

    if (parity.even()) {
      set.set(0);
    } else {
      set.set(1);
    }

    return colours < set.length() ? Optional.empty() : Optional.of(set);
  }

  @Override
  public Optional<BitSet> rejectingSet() {
    BitSet set = new BitSet();

    if (parity.even()) {
      set.set(1);
    } else {
      set.set(0);
    }

    return colours < set.length() ? Optional.empty() : Optional.of(set);
  }

  public Parity parity() {
    return parity;
  }

  public ParityAcceptance withParity(Parity parity) {
    return new ParityAcceptance(colours, parity);
  }

  public ParityAcceptance complement() {
    return new ParityAcceptance(colours, parity.flipEven());
  }

  public boolean emptyIsAccepting() {
    return parity == Parity.MIN_EVEN || parity == Parity.MAX_ODD;
  }

  @Override
  public int acceptanceSets() {
    return colours;
  }

  @Override
  public PropositionalFormula<Integer> booleanExpression() {
    if (colours == 0) {
      return constant(emptyIsAccepting());
    }

    PropositionalFormula<Integer> exp;

    if (parity.max()) {
      exp = mkColor(0);
      for (int i = 1; i < colours; i++) {
        exp = isAccepting(i) ? Disjunction.of(mkColor(i), exp) : Conjunction.of(mkColor(i), exp);
      }
    } else {
      exp = mkColor(colours - 1);
      for (int i = colours - 2; i >= 0; i--) {
        exp = isAccepting(i) ? Disjunction.of(mkColor(i), exp) : Conjunction.of(mkColor(i), exp);
      }
    }

    return exp;
  }

  private PropositionalFormula<Integer> mkColor(int priority) {
    return isAccepting(priority)
      ? Variable.of(priority)
      : Negation.of(Variable.of(priority));
  }

  public boolean isAccepting(int priority) {
    return parity.isAccepting(priority);
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.colours().isEmpty()
      || (edge.colours().size() == 1 && edge.colours().last() < colours);
  }

  public ParityAcceptance withAcceptanceSets(@Nonnegative int colours) {
    return new ParityAcceptance(colours, parity);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ParityAcceptance that = (ParityAcceptance) o;
    return colours == that.colours && parity == that.parity;
  }

  @Override
  public int hashCode() {
    return (31 * (31 + colours)) + parity.hashCode();
  }

  @SuppressWarnings("MethodReturnAlwaysConstant")
  public enum Parity {
    MIN_EVEN, MIN_ODD, MAX_EVEN, MAX_ODD;

    @SuppressWarnings("BooleanParameter")
    public static Parity of(boolean max, boolean even) {
      if (max && even) {
        return MAX_EVEN;
      }

      if (max) {
        return MAX_ODD;
      }

      if (even) {
        return MIN_EVEN;
      }

      return MIN_ODD;
    }

    public Parity flipMax() {
      switch (this) {
        case MIN_ODD:
          return MAX_ODD;
        case MIN_EVEN:
          return MAX_EVEN;
        case MAX_EVEN:
          return MIN_EVEN;
        case MAX_ODD:
          return MIN_ODD;
        default:
          throw new AssertionError();
      }
    }

    public Parity flipEven() {
      switch (this) {
        case MIN_ODD:
          return MIN_EVEN;
        case MIN_EVEN:
          return MIN_ODD;
        case MAX_EVEN:
          return MAX_ODD;
        case MAX_ODD:
          return MAX_EVEN;
        default:
          throw new AssertionError();
      }
    }

    public boolean even() {
      return equals(MIN_EVEN) || equals(MAX_EVEN);
    }

    public boolean max() {
      return equals(MAX_EVEN) || equals(MAX_ODD);
    }

    public Parity setEven(boolean even) {
      return even == even() ? this : flipEven();
    }

    public Parity setMax(boolean max) {
      return max == max() ? this : flipMax();
    }

    public String evenString() {
      return even() ? "even" : "odd";
    }

    public String maxString() {
      return max() ? "max" : "min";
    }

    @Override
    public String toString() {
      return maxString() + ' ' + evenString();
    }

    public boolean isAccepting(int priority) {
      return priority % 2 == 0 ^ !even();
    }
  }
}
