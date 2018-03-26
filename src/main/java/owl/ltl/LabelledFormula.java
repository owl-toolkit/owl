package owl.ltl;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.immutables.value.Value;
import owl.collections.Collections3;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.Visitor;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class LabelledFormula {
  public abstract Formula formula();

  public abstract List<String> variables();

  public abstract Set<String> player1Variables();

  @Value.Check
  void check() {
    checkState(Collections3.isDistinct(variables()));
    checkState(variables().containsAll(player1Variables()));
  }


  public static LabelledFormula of(Formula formula, List<String> variables) {
    return LabelledFormulaTuple.create(formula, variables, variables);
  }

  public static LabelledFormula of(Formula formula, List<String> variables, Set<String> player1) {
    return LabelledFormulaTuple.create(formula, variables, player1);
  }

  public static LabelledFormula of(Formula formula, List<String> variables, BitSet player1) {
    Set<String> player1Variables = new HashSet<>();
    BitSets.forEach(player1, i -> player1Variables.add(variables.get(i)));
    return LabelledFormulaTuple.create(formula, variables, Set.copyOf(player1Variables));
  }


  public LabelledFormula wrap(Formula formula) {
    return of(formula, variables(), player1Variables());
  }

  public LabelledFormula split(Set<String> player1) {
    return of(formula(), variables(), player1);
  }

  public LabelledFormula not() {
    return wrap(formula().not());
  }


  Set<String> player2Variables() {
    Set<String> player1 = player1Variables();
    return Set.copyOf(Sets.difference(Set.copyOf(variables()), player1));
  }

  public int accept(IntVisitor visitor) {
    return formula().accept(visitor);
  }

  public <R> R accept(Visitor<R> visitor) {
    return formula().accept(visitor);
  }

  public <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter) {
    return formula().accept(visitor, parameter);
  }

  public LabelledFormula convert(Visitor<Formula> visitor) {
    return wrap(formula().accept(visitor));
  }


  public boolean allMatch(Predicate<Formula> predicate) {
    return formula().allMatch(predicate);
  }

  public boolean anyMatch(Predicate<Formula> predicate) {
    return formula().anyMatch(predicate);
  }

  @Override
  public String toString() {
    return PrintVisitor.toString(this, false);
  }
}
