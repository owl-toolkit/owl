package owl.ltl;

import com.google.common.collect.ImmutableList;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.collections.Collections3;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.Visitor;

public final class LabelledFormula {
  public final Formula formula;
  public final ImmutableList<String> variables;

  @Nullable
  private final BitSet player1;

  private LabelledFormula(ImmutableList<String> variables, Formula formula,
    @Nullable BitSet player1) {
    this.variables = variables;
    this.formula = formula;
    this.player1 = player1;
  }

  public static LabelledFormula of(Formula formula, List<String> variables) {
    return new LabelledFormula(ImmutableList.copyOf(variables), formula, null);
  }

  public static LabelledFormula of(Formula formula, List<String> variables,
    BitSet player1Variables) {
    return new LabelledFormula(ImmutableList.copyOf(variables), formula,
      BitSets.copyOf(player1Variables));
  }

  public int accept(IntVisitor visitor) {
    return formula.accept(visitor);
  }

  public <R> R accept(Visitor<R> visitor) {
    return formula.accept(visitor);
  }

  public <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter) {
    return formula.accept(visitor, parameter);
  }

  public LabelledFormula acceptConverter(Visitor<Formula> visitor) {
    return of(formula.accept(visitor), variables);
  }

  public boolean allMatch(Predicate<Formula> predicate) {
    return formula.allMatch(predicate);
  }

  public boolean anyMatch(Predicate<Formula> predicate) {
    return formula.anyMatch(predicate);
  }

  public Formula getFormula() {
    return formula;
  }

  public LabelledFormula not() {
    return of(formula.not(), variables);
  }

  @Override
  public String toString() {
    return PrintVisitor.toString(this, false);
  }

  public LabelledFormula wrap(Formula formula) {
    return of(formula, variables);
  }

  public LabelledFormula wrap(BitSet player1Variables) {
    return of(formula, variables, player1Variables);
  }

  public List<String> getPlayer1Variables() {
    List<String> variables = new ArrayList<>();

    Collections3.forEachIndexed(variables, (i, e) -> {
      if (player1 != null && player1.get(i)) {
        variables.add(e);
      }
    });

    return variables;
  }

  public List<String> getPlayer2Variables() {
    List<String> variables = new ArrayList<>();

    Collections3.forEachIndexed(variables, (i, e) -> {
      if (player1 != null && !player1.get(i)) {
        variables.add(e);
      }
    });

    return variables;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LabelledFormula that = (LabelledFormula) o;
    return Objects.equals(formula, that.formula)
      && Objects.equals(variables, that.variables)
      && Objects.equals(player1, that.player1);
  }

  @Override
  public int hashCode() {
    return Objects.hash(formula, variables, player1);
  }
}
