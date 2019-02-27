package owl.translations.mastertheorem;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import owl.collections.UpwardClosedSet;
import owl.ltl.BinaryModalOperator;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.visitors.Visitor;

public final class Selector {

  private Selector() {}

  public static Set<Fixpoints> selectAsymmetric(Formula formula, boolean all) {
    if (all) {
      return Sets.powerSet(selectGreatestFixpoints(formula))
        .stream()
        .map(x -> Fixpoints.of(Set.of(), x))
        .collect(toSet());
    } else {
      return NormalForms
        .toDnf(formula, NormalForms.SYNTHETIC_CO_SAFETY_LITERAL)
        .stream()
        .flatMap(Selector::selectAsymmetricFromClause)
        .collect(toSet());
    }
  }

  public static Set<Fixpoints> selectSymmetric(Formula formula, boolean all) {
    if (all) {
      return Sets.powerSet(selectAllFixpoints(formula))
        .stream()
        .map(Fixpoints::of)
        .collect(toSet());
    } else {
      return NormalForms
        .toDnf(formula, NormalForms.SYNTHETIC_CO_SAFETY_LITERAL)
        .stream()
        .flatMap(Selector::selectSymmetricFromClause)
        .collect(toSet());
    }
  }

  private static Stream<Fixpoints> selectAsymmetricFromClause(Set<Formula> clause) {
    List<Set<Set<Formula.ModalOperator>>> elementSets = new ArrayList<>();

    for (Formula element : clause) {
      assert isClauseElement(element);

      var fixpoints = selectGreatestFixpoints(element);

      if (!fixpoints.isEmpty()) {
        elementSets.add(Sets.powerSet(fixpoints));
      }
    }

    List<Fixpoints> fixpointsList = new ArrayList<>();

    for (List<Set<Formula.ModalOperator>> combination : Sets.cartesianProduct(elementSets)) {
      Set<Formula.ModalOperator> union = new HashSet<>();
      combination.forEach(union::addAll);
      fixpointsList.add(Fixpoints.of(Set.of(), union));
    }

    return fixpointsList.stream();
  }

  private static Stream<Fixpoints> selectSymmetricFromClause(Set<Formula> clause) {
    List<Fixpoints> fixpointsList = new ArrayList<>();
    List<Set<Set<Formula.ModalOperator>>> elementSets = new ArrayList<>();

    for (Formula element : clause) {
      assert isClauseElement(element);

      if (SyntacticFragment.CO_SAFETY.contains(element)) {
        continue;
      }

      LinkedHashMap<Formula.ModalOperator, Integer> literalMapping = new LinkedHashMap<>();
      Set<Set<Formula.ModalOperator>> fixpoints = new HashSet<>();
      UnscopedVisitor visitor = new UnscopedVisitor(literalMapping);
      UpwardClosedSet set = element.accept(visitor);
      List<Formula.ModalOperator> mapping = List.copyOf(literalMapping.keySet());

      for (BitSet mask : BitSets.powerSet(literalMapping.size())) {
        if (set.contains(mask)) {
          fixpoints.add(mask.stream().mapToObj(mapping::get).collect(toSet()));
        }
      }

      elementSets.add(fixpoints);
    }

    for (List<Set<Formula.ModalOperator>> combination : Sets.cartesianProduct(elementSets)) {
      Set<Formula.ModalOperator> union = new HashSet<>();
      combination.forEach(union::addAll);
      fixpointsList.add(Fixpoints.of(union));
    }

    return fixpointsList.stream();
  }

  private static boolean isClauseElement(Formula formula) {
    return SyntacticFragment.CO_SAFETY.contains(formula)
      || formula instanceof Literal
      || formula instanceof UnaryModalOperator
      || formula instanceof BinaryModalOperator;
  }

  private static Set<Formula.ModalOperator> selectAllFixpoints(
    Formula formula) {
    return formula.subformulas(Predicates.IS_FIXPOINT,
      Formula.ModalOperator.class::cast);
  }

  private static Set<Formula.ModalOperator> selectGreatestFixpoints(
    Formula formula) {
    return formula.subformulas(Predicates.IS_GREATEST_FIXPOINT,
      Formula.ModalOperator.class::cast);
  }

  private abstract static class AbstractSymmetricVisitor implements Visitor<UpwardClosedSet> {

    @Override
    public UpwardClosedSet visit(Conjunction conjunction) {
      UpwardClosedSet set = UpwardClosedSet.of(new BitSet());

      for (Formula x : conjunction.children) {
        set = set.intersection(x.accept(this));
      }

      return set;
    }

    @Override
    public UpwardClosedSet visit(Disjunction disjunction) {
      UpwardClosedSet set = UpwardClosedSet.of();

      for (Formula x : disjunction.children) {
        set = set.union(x.accept(this));
      }

      return set;
    }

    @Override
    public final UpwardClosedSet visit(Literal literal) {
      return UpwardClosedSet.of(new BitSet());
    }

    @Override
    public final UpwardClosedSet visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }

  private static final class UnscopedVisitor extends AbstractSymmetricVisitor {
    private final GScopedVisitor gScopedVisitor;

    private UnscopedVisitor(Map<Formula.ModalOperator, Integer> literals) {
      gScopedVisitor = new GScopedVisitor(literals);
    }

    @Override
    public UpwardClosedSet visit(FOperator fOperator) {
      return fOperator.operand.accept(this);
    }

    @Override
    public UpwardClosedSet visit(GOperator gOperator) {
      return gOperator.operand.accept(gScopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(MOperator mOperator) {
      if (SyntacticFragment.CO_SAFETY.contains(mOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return mOperator.left.accept(this).intersection(mOperator.right.accept(this));
    }

    @Override
    public UpwardClosedSet visit(ROperator rOperator) {
      if (SyntacticFragment.SAFETY.contains(rOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return rOperator.left.accept(this).union(rOperator.right.accept(gScopedVisitor));
    }

    @Override
    public UpwardClosedSet visit(UOperator uOperator) {
      if (SyntacticFragment.CO_SAFETY.contains(uOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return uOperator.left.accept(this).union(uOperator.right.accept(this));
    }

    @Override
    public UpwardClosedSet visit(WOperator wOperator) {
      if (SyntacticFragment.SAFETY.contains(wOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return wOperator.left.accept(gScopedVisitor).union(wOperator.right.accept(this));
    }
  }

  private static class GScopedVisitor extends AbstractSymmetricVisitor {
    ScopedVisitor scopedVisitor;

    private GScopedVisitor(Map<Formula.ModalOperator, Integer> literals) {
      this.scopedVisitor = new ScopedVisitor(literals);
    }

    @Override
    public UpwardClosedSet visit(FOperator fOperator) {
      return fOperator.accept(scopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(GOperator gOperator) {
      return gOperator.operand.accept(this);
    }

    @Override
    public UpwardClosedSet visit(MOperator mOperator) {
      return mOperator.accept(scopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(ROperator rOperator) {
      return rOperator.left.accept(this).union(rOperator.right.accept(this));
    }

    @Override
    public UpwardClosedSet visit(UOperator uOperator) {
      return uOperator.accept(scopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(WOperator wOperator) {
      return wOperator.left.accept(this).union(wOperator.right.accept(this));
    }
  }

  private static class ScopedVisitor extends AbstractSymmetricVisitor {
    private final Map<Formula.ModalOperator, Integer> literals;

    private ScopedVisitor(Map<Formula.ModalOperator, Integer> literals) {
      this.literals = literals;
    }

    @Override
    public UpwardClosedSet visit(FOperator fOperator) {
      if (SyntacticFragment.CO_SAFETY.contains(fOperator)) {
        return singleton(fOperator);
      }

      return fOperator.operand.accept(this).intersection(singleton(fOperator));
    }

    @Override
    public UpwardClosedSet visit(GOperator gOperator) {
      if (SyntacticFragment.SAFETY.contains(gOperator)) {
        return singleton(gOperator);
      }

      return gOperator.operand.accept(this).intersection(singleton(gOperator));
    }

    // Binary Modal Operators

    @Override
    public UpwardClosedSet visit(MOperator mOperator) {
      return visit((BinaryModalOperator) mOperator);
    }

    @Override
    public UpwardClosedSet visit(ROperator rOperator) {
      return visit((BinaryModalOperator) rOperator);
    }

    @Override
    public UpwardClosedSet visit(UOperator uOperator) {
      return visit((BinaryModalOperator) uOperator);
    }

    @Override
    public UpwardClosedSet visit(WOperator wOperator) {
      return visit((BinaryModalOperator) wOperator);
    }

    private UpwardClosedSet visit(BinaryModalOperator binaryModalOperator) {
      // We just explore for more literals, but actually we can't reason anymore...
      singleton(binaryModalOperator);
      binaryModalOperator.left.accept(this);
      binaryModalOperator.right.accept(this);
      return UpwardClosedSet.of(new BitSet());
    }

    protected UpwardClosedSet singleton(Formula.ModalOperator modalOperator) {
      BitSet bitSet = new BitSet();
      bitSet.set(literals.computeIfAbsent(modalOperator, x -> literals.size()));
      return UpwardClosedSet.of(bitSet);
    }
  }
}
