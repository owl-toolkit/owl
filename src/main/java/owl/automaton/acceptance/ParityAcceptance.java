/*
 * Copyright (C) 2016  (See AUTHORS)
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
import java.util.List;
import java.util.PrimitiveIterator;
import javax.annotation.Nonnegative;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaConsumerExtended;

public final class ParityAcceptance implements OmegaAcceptance {
  @Nonnegative
  private int colours;
  private Priority priority;

  public ParityAcceptance(@Nonnegative int colours) {
    this(colours, Priority.ODD);
  }

  public ParityAcceptance(@Nonnegative int colours, Priority priority) {
    this.colours = colours;
    this.priority = priority;
  }

  public void complement() {
    priority = priority.not();
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
    return colours == that.colours && priority == that.priority;
  }

  @Override
  public int getAcceptanceSets() {
    return colours;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    if (colours == 0) {
      return new BooleanExpression<>(priority == Priority.EVEN);
    }

    int index = colours - 1;

    BooleanExpression<AtomAcceptance> exp = mkColor(index);

    for (index--; 0 <= index; index--) {
      if (index % 2 == 0 ^ priority == Priority.EVEN) {
        exp = mkColor(index).and(exp);
      } else {
        exp = mkColor(index).or(exp);
      }
    }

    return exp;
  }

  @Override
  public String getName() {
    return "parity";
  }

  @Override
  public List<Object> getNameExtra() {
    return Arrays.asList("min", priority.toString(), colours);
  }

  public Priority getPriority() {
    return priority;
  }

  @Override
  public int hashCode() {
    return 31 * colours + priority.hashCode();
  }

  public boolean isAccepting(int priority) {
    return priority % 2 == 0 ^ this.priority == Priority.ODD;
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    PrimitiveIterator.OfInt iterator = edge.acceptanceSetIterator();
    if (!iterator.hasNext()) {
      return true;
    }
    int firstIndex = iterator.nextInt();
    return !iterator.hasNext() && firstIndex < colours;
  }

  private BooleanExpression<AtomAcceptance> mkColor(int i) {
    return (i % 2 == 0 ^ priority == Priority.EVEN)
      ? HoaConsumerExtended.mkFin(i)
      : HoaConsumerExtended.mkInf(i);
  }

  public void setAcceptanceSets(@Nonnegative int colors) {
    this.colours = colors;
  }

  @SuppressWarnings("MethodReturnAlwaysConstant")
  public enum Priority {
    EVEN {
      @Override
      public Priority not() {
        return ODD;
      }

      @Override
      public String toString() {
        return "even";
      }
    },

    ODD {
      @Override
      public Priority not() {
        return EVEN;
      }

      @Override
      public String toString() {
        return "odd";
      }
    };

    public abstract Priority not();
  }


}
