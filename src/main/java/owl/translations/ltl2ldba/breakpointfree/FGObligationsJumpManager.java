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

package owl.translations.ltl2ldba.breakpointfree;

import com.google.common.collect.Collections2;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.ltl.visitors.Converter;
import owl.translations.ltl2ldba.AbstractJumpManager;
import owl.translations.ltl2ldba.Jump;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class FGObligationsJumpManager extends AbstractJumpManager<FGObligations> {

  private final Table<Set<FOperator>, Set<GOperator>, FGObligations> cache;

  private FGObligationsJumpManager(Factories factories,
    Set<Configuration> optimisations, Set<Formula.ModalOperator> modalOperators,
    Formula initialFormula) {
    super(optimisations, factories, modalOperators, initialFormula);
    cache = HashBasedTable.create();
  }

  public static FGObligationsJumpManager build(Formula formula, Factories factories,
    Set<Configuration> optimisations) {
    return new FGObligationsJumpManager(factories, optimisations,
      factories.eqFactory.of(formula).modalOperators(), formula);
  }

  private static Stream<Map.Entry<Set<FOperator>, Set<GOperator>>> createFGSetStream(
    Formula state) {
    Set<GOperator> gOperators = state.subformulas(
      x -> x instanceof GOperator || x instanceof ROperator || x instanceof WOperator,
      x -> {
        if (x instanceof ROperator) {
          return new GOperator(((ROperator) x).right);
        }

        if (x instanceof WOperator) {
          return new GOperator(((WOperator) x).left);
        }

        return (GOperator) x;
      });

    Set<FOperator> fOperators = state.subformulas(
      x -> x instanceof FOperator || x instanceof UOperator || x instanceof MOperator,
      x -> {
        if (x instanceof UOperator) {
          return new FOperator(((UOperator) x).right);
        }

        if (x instanceof MOperator) {
          return new FOperator(((MOperator) x).left);
        }

        return (FOperator) x;
      });

    // Pre-filter
    gOperators.removeIf(x -> x.operand instanceof FOperator);
    fOperators.removeIf(x -> x.operand instanceof GOperator);

    SetMultimap<Set<FOperator>, Set<GOperator>> multimap = MultimapBuilder
      .hashKeys()
      .hashSetValues()
      .build();

    for (Set<FOperator> fSet : Sets.powerSet(fOperators)) {
      for (Set<GOperator> gSet : Sets.powerSet(gOperators)) {
        multimap.put(fSet, gSet);
      }
    }

    return multimap.entries().stream();
  }

  // TODO: also use GOps Information
  static Formula replaceFOperators(Set<FOperator> trueFOperators,
    Set<GOperator> trueGOperators, GOperator formula) {
    ReplaceFOperatorsVisitor visitor = new ReplaceFOperatorsVisitor(trueFOperators, trueGOperators);
    return GOperator.of(formula.operand.accept(visitor));
  }

  static Formula replaceGOperators(Set<GOperator> trueGOperators,
    Set<FOperator> trueFOperators, Formula formula) {
    ReplaceGOperatorsVisitor visitor = new ReplaceGOperatorsVisitor(trueGOperators, trueFOperators);
    return formula.accept(visitor);
  }

  @Override
  protected Set<Jump<FGObligations>> computeJumps(EquivalenceClass state) {
    Set<Jump<FGObligations>> fgObligations = new TreeSet<>();

    createDisjunctionStream(state, FGObligationsJumpManager::createFGSetStream).forEach(entry -> {
      Set<FOperator> fOperators = Set.copyOf(entry.getKey());
      Set<GOperator> gOperators = Set.copyOf(entry.getValue());

      FGObligations obligations = cache.get(fOperators, gOperators);

      if (obligations == null) {
        obligations = FGObligations.build(fOperators, gOperators, factories,
          configuration.contains(Configuration.EAGER_UNFOLD));

        if (obligations != null) {
          cache.put(fOperators, gOperators, obligations);
        }
      }

      if (obligations == null) {
        Logger.getGlobal().log(Level.FINER, () -> "Did not create FGObligations for " + entry);
        return;
      }

      EquivalenceClass remainder = evaluate(state, obligations);

      if (!remainder.isFalse()) {
        fgObligations.add(buildJump(remainder, obligations));
      }
    });

    return fgObligations;
  }

  private EquivalenceClass evaluate(EquivalenceClass clazz, FGObligations obligation) {
    // TODO: use substitute
    Formula formula = clazz.representative();
    ReplaceFOperatorsVisitor visitor = new ReplaceFOperatorsVisitor(obligation.fOperators,
      obligation.gOperators);
    Formula fFreeFormula = visitor.apply(formula);
    Formula evaluated = SimplifierFactory.apply(fFreeFormula, Mode.SYNTACTIC);
    Logger.getGlobal().log(Level.FINER, () -> "Rewrote " + clazz + " into " + evaluated
      + " using " + obligation);
    return factories.eqFactory.of(evaluated);
  }

  static class ReplaceFOperatorsVisitor extends Converter {
    private final Set<FOperator> foperators;
    private final Set<GOperator> goperators;

    ReplaceFOperatorsVisitor(Set<FOperator> foperators, Set<GOperator> goperators) {
      super(SyntacticFragment.NNF.classes());
      this.foperators = Set.copyOf(foperators);
      this.goperators = Set.copyOf(Sets.newHashSet(Iterables.concat(goperators,
        Collections2.transform(foperators, GOperator::new))));
    }

    private boolean isTrueFOperator(FOperator fOperator) {
      return ((fOperator.operand instanceof GOperator) && goperators.contains(fOperator.operand))
        || foperators.contains(fOperator);
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return BooleanConstant.of(isTrueFOperator(fOperator));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (gOperator.operand instanceof FOperator && foperators.contains(gOperator.operand)) {
        return BooleanConstant.TRUE;
      }

      return BooleanConstant.of(goperators.contains(gOperator));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (isTrueFOperator(new FOperator(mOperator.left))) {
        return ROperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (isTrueFOperator(new FOperator(uOperator.right))) {
        return WOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(ROperator rOperator) {
      return ROperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      return WOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
    }
  }

  static class ReplaceGOperatorsVisitor extends Converter {
    private final Set<FOperator> foperators;
    private final Set<GOperator> goperators;

    ReplaceGOperatorsVisitor(Set<GOperator> goperators, Set<FOperator> foperators) {
      super(SyntacticFragment.NNF.classes());
      this.goperators = Set.copyOf(goperators);
      this.foperators = Set.copyOf(foperators);
    }

    private boolean isTrueGOperator(GOperator gOperator) {
      return (gOperator.operand instanceof FOperator && foperators.contains(gOperator.operand))
        || goperators.contains(gOperator);
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return FOperator.of(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return BooleanConstant.of(isTrueGOperator(gOperator));
    }

    @Override
    public Formula visit(Literal literal) {
      // TODO: extend this?
      if (goperators.contains(new GOperator(literal))) {
        return BooleanConstant.TRUE;
      }

      return literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (isTrueGOperator(new GOperator(rOperator.right))) {
        return BooleanConstant.TRUE;
      }

      return MOperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (isTrueGOperator(new GOperator(wOperator.left))) {
        return BooleanConstant.TRUE;
      }

      return UOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
    }
  }
}
