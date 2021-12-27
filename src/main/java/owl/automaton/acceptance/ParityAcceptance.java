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

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;
import static owl.logic.propositional.PropositionalFormula.constant;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnegative;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

public final class ParityAcceptance extends EmersonLeiAcceptance {
  private final Parity parity;

  public ParityAcceptance(@Nonnegative int colours, Parity parity) {
    super(colours);
    this.parity = parity;
  }

  @Override
  public String name() {
    return "parity";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of(parity.maxString(), parity.evenString(), acceptanceSets());
  }

  @Override
  public Optional<ImmutableBitSet> acceptingSet() {
    if (parity.even()) {
      return acceptanceSets() <= 0
        ? Optional.empty()
        : Optional.of(ImmutableBitSet.of(0));
    } else {
      return acceptanceSets() <= 1
        ? Optional.empty()
        : Optional.of(ImmutableBitSet.of(1));
    }
  }

  @Override
  public Optional<ImmutableBitSet> rejectingSet() {
    if (parity.even()) {
      return acceptanceSets() <= 1
        ? Optional.empty()
        : Optional.of(ImmutableBitSet.of(1));
    } else {
      return acceptanceSets() <= 0
        ? Optional.empty()
        : Optional.of(ImmutableBitSet.of(0));
    }
  }

  public Parity parity() {
    return parity;
  }

  public ParityAcceptance withParity(Parity parity) {
    return new ParityAcceptance(acceptanceSets(), parity);
  }

  public ParityAcceptance complement() {
    return new ParityAcceptance(acceptanceSets(), parity.flipEven());
  }

  public boolean emptyIsAccepting() {
    return parity == Parity.MIN_EVEN || parity == Parity.MAX_ODD;
  }

  @Override
  protected PropositionalFormula<Integer> lazyBooleanExpression() {
    if (acceptanceSets() == 0) {
      return constant(emptyIsAccepting());
    }

    PropositionalFormula<Integer> exp;

    if (parity.max()) {
      exp = mkColor(0);
      for (int i = 1; i < acceptanceSets(); i++) {
        exp = isAccepting(i) ? Disjunction.of(mkColor(i), exp) : Conjunction.of(mkColor(i), exp);
      }
    } else {
      exp = mkColor(acceptanceSets() - 1);
      for (int i = acceptanceSets() - 2; i >= 0; i--) {
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

  public ParityAcceptance withAcceptanceSets(@Nonnegative int colours) {
    return new ParityAcceptance(colours, parity);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ParityAcceptance that
      && acceptanceSets() == that.acceptanceSets() && parity == that.parity;
  }

  @Override
  public int hashCode() {
    return (31 * (31 + acceptanceSets())) + parity.hashCode();
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
      return switch (this) {
        case MIN_ODD -> MAX_ODD;
        case MIN_EVEN -> MAX_EVEN;
        case MAX_EVEN -> MIN_EVEN;
        case MAX_ODD -> MIN_ODD;
      };
    }

    public Parity flipEven() {
      return switch (this) {
        case MIN_ODD -> MIN_EVEN;
        case MIN_EVEN -> MIN_ODD;
        case MAX_EVEN -> MAX_ODD;
        case MAX_ODD -> MAX_EVEN;
      };
    }

    public boolean even() {
      return equals(MIN_EVEN) || equals(MAX_EVEN);
    }

    public boolean max() {
      return equals(MAX_EVEN) || equals(MAX_ODD);
    }

    public String evenString() {
      return even() ? "even" : "odd";
    }

    public String maxString() {
      return max() ? "max" : "min";
    }

    public boolean isAccepting(int priority) {
      return priority % 2 == 0 ^ !even();
    }
  }
}
