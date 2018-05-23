package owl.translations.fgx2dpa;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.acceptance.ParityAcceptance.Parity;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.PropositionalFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.UnaryModalOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.PropositionalVisitor;
import owl.ltl.visitors.SubstitutionVisitor;
import owl.ltl.visitors.Visitor;
import owl.run.Environment;

public final class SafetyAutomaton {
  private SafetyAutomaton() {}

  public static MutableAutomaton<State, ParityAcceptance>
  build(Environment env, LabelledFormula labelledFormula) {
    checkArgument(SyntacticFragment.FGX.contains(labelledFormula.formula())
        && SyntacticFragment.NNF.contains(labelledFormula.formula()),
      "Formula is not from the syntactic fgx and nnf fragment.");

    Set<Set<UnaryModalOperator>> promisedSetsFormula =
      labelledFormula.formula().accept(OutsideGVisitor.INSTANCE);
    int promisedSetCount = promisedSetsFormula.size();

    if (promisedSetCount == 1
      && Iterables.getOnlyElement(promisedSetsFormula).isEmpty()) {
      return construct(labelledFormula, promisedSetsFormula, Parity.MIN_EVEN, env);
    }

    LabelledFormula labelledFormulaNegation = labelledFormula.not();
    Set<Set<UnaryModalOperator>> promisedSetsNegation =
      labelledFormulaNegation.formula().accept(OutsideGVisitor.INSTANCE);
    int negationPromisedSetCount = promisedSetsNegation.size();

    if (negationPromisedSetCount == 1
      && Iterables.getOnlyElement(promisedSetsNegation).isEmpty()) {
      return construct(labelledFormulaNegation, promisedSetsNegation, Parity.MIN_ODD, env);
    }

    BigInteger estimateFormula = estimate(promisedSetsFormula);
    BigInteger estimateNegation = estimate(promisedSetsNegation);
    int compare = estimateFormula.compareTo(estimateNegation);
    if (compare < 0 || (compare == 0 && promisedSetCount <= negationPromisedSetCount)) {
      return construct(labelledFormula, promisedSetsFormula, Parity.MIN_EVEN, env);
    }
    return construct(labelledFormulaNegation, promisedSetsNegation, Parity.MIN_ODD, env);
  }

  private static BigInteger estimate(Set<Set<UnaryModalOperator>> promisedSets) {
    BigInteger estimatedPermutations = BigInteger.ONE;

    long nonFinalG = promisedSets.stream().filter(l -> l.stream().anyMatch(g ->
      g instanceof GOperator && !(g.operand.accept(FinalStateVisitor.INSTANCE)))).count();
    for (Set<UnaryModalOperator> list : promisedSets) {
      long count = list.stream().filter(f -> f instanceof FOperator
        && !(f.operand.accept(FinalStateVisitor.INSTANCE))).count();
      if (count > 1) {
        estimatedPermutations = estimatedPermutations.multiply(BigInteger.valueOf(count));
      }
    }
    for (long i = nonFinalG; i >= 2; i--) {
      estimatedPermutations = estimatedPermutations.multiply(BigInteger.valueOf(i));
    }

    return estimatedPermutations;
  }

  private static MutableAutomaton<State, ParityAcceptance> construct(
    LabelledFormula labelledFormula, Set<Set<UnaryModalOperator>> promisedSets,
    Parity parity, Environment env) {

    Formula formula = labelledFormula.formula();
    Formula unfolded = formula.unfold();

    Set<GOperator> gFormulae = new HashSet<>();
    Set<FOperator> fFormulae = new HashSet<>();
    for (UnaryModalOperator modal : Iterables.concat(promisedSets)) {
      if (modal instanceof GOperator) {
        gFormulae.add((GOperator) modal);
      } else {
        fFormulae.add((FOperator) modal);
      }
    }

    Set<Monitor<GOperator>> monitorsG = Collections3.transformUnique(gFormulae, Monitor::of);
    Set<Monitor<FOperator>> monitorsF = Collections3.transformUnique(fFormulae, Monitor::of);

    List<PromisedSet> initialPermutation = new ArrayList<>(promisedSets.size());
    for (Set<UnaryModalOperator> set : promisedSets) {
      Set<GOperator> formulaeG = set.stream().filter(GOperator.class::isInstance)
        .map(GOperator.class::cast).collect(Collectors.toUnmodifiableSet());
      List<FOperator> formulaeF = set.stream().filter(FOperator.class::isInstance)
        .map(FOperator.class::cast)
        .sorted(Comparator.comparingInt(f -> f.operand.accept(FinalStateVisitor.INSTANCE) ? 1 : 0))
        .collect(Collectors.toUnmodifiableList());
      initialPermutation.add(PromisedSet.of(formulaeG, formulaeF));
    }

    initialPermutation = initialPermutation.stream()
      .sorted(Comparator.comparingLong(PromisedSet::nonFinalGCount).reversed())
      .collect(Collectors.toUnmodifiableList());

    Factories factories = env.factorySupplier().getFactories(labelledFormula.variables());
    EquivalenceClassFactory eqFactory = factories.eqFactory;

    int initialPriority;
    if (formula.equals(BooleanConstant.TRUE)) {
      initialPriority = 2;
    } else if (formula.equals(BooleanConstant.FALSE)) {
      initialPriority = 3;
    } else {
      initialPriority = initialPermutation.size() * 2 + 1;
    }

    State initialState =
      State.of(unfolded, monitorsG, monitorsF, initialPriority, initialPermutation);

    ParityAcceptance acceptance = new ParityAcceptance(initialPermutation.size() * 2 + 2, parity);

    MutableAutomaton<State, ParityAcceptance> automaton =
      MutableAutomatonFactory.create(acceptance, factories.vsFactory);

    AutomatonUtil.exploreDeterministic(automaton, Set.of(initialState), (state, valuation) -> {
      Formula successor = state.formula().temporalStepUnfold(valuation);
      if (successor instanceof BooleanConstant) {
        return Edge.of(State.of((BooleanConstant) successor), state.priority());
      }

      Set<Monitor<GOperator>> newMonitorsG;
      Set<Monitor<FOperator>> newMonitorsF;
      List<PromisedSet> previousPermutation;

      if (successor.equals(state.formula())) {
        newMonitorsG = state.monitorsG().stream().map(s -> s.temporalStep(valuation))
          .collect(Collectors.toUnmodifiableSet());
        newMonitorsF = state.monitorsF().stream().map(s -> s.temporalStep(valuation))
          .collect(Collectors.toUnmodifiableSet());
        previousPermutation = state.permutation();
      } else {
        Set<GOperator> gOperators = Collector.collectGOperators(state.formula());
        Set<FOperator> fOperators = new HashSet<>();
        for (GOperator g : gOperators) {
          fOperators.addAll(Collector.collectFOperators(g));
        }

        Set<UnaryModalOperator> formulas = Sets.union(gOperators, fOperators);
        newMonitorsG = state.monitorsG().stream()
          .filter(s -> gOperators.contains(s.formula()))
          .map(s -> s.temporalStep(valuation))
          .collect(Collectors.toUnmodifiableSet());

        newMonitorsF = state.monitorsF().stream()
          .filter(s -> fOperators.contains(s.formula()))
          .map(s -> s.temporalStep(valuation))
          .collect(Collectors.toUnmodifiableSet());

        previousPermutation = state.permutation().stream().filter(
          p -> formulas.containsAll(p.union())).collect(Collectors.toUnmodifiableList());
      }

      int priority = previousPermutation.size() * 2 + 1;
      List<PromisedSet> currentPermutation = new ArrayList<>(previousPermutation.size());
      List<PromisedSet> goodOrNeutral = new ArrayList<>();

      for (int promIndex = previousPermutation.size() - 1; promIndex >= 0; promIndex--) {
        PromisedSet promisedSet = previousPermutation.get(promIndex);
        Set<Formula> alpha = new HashSet<>();
        if (promisedSet.formulaeF().isEmpty()) {
          alpha.add(BooleanConstant.TRUE);
        } else {
          alpha.addAll(promisedSet.formulaeF());
        }

        var violationVisitor = new PromisedSetAcceptanceVisitor(promisedSet, false);
        boolean violation = false;
        for (Formula g : promisedSet.formulaeG()) {
          Monitor<GOperator> monitor = newMonitorsG.stream()
            .filter(s -> s.formula().equals(g))
            .findFirst().orElseThrow();

          if (Iterables.any(monitor.finalStates(), t -> t.accept(violationVisitor))) {
            violation = true;
            break;
          }

          alpha.add(g);
          SubstitutionVisitor substitution = new SubstitutionVisitor(f -> {
            if (f instanceof FOperator) {
              return promisedSet.formulaeF().contains(f) ? f : BooleanConstant.FALSE;
            }
            if (f instanceof GOperator) {
              return promisedSet.formulaeG().contains(f) ? f : BooleanConstant.FALSE;
            }
            return f;
          });

          monitor.nonFinalStates().forEach(t -> alpha.add(t.accept(substitution)));
        }

        if (violation) {
          currentPermutation.add(0, promisedSet);
          priority = Integer.min((previousPermutation.size() - promIndex) * 2 - 1, priority);
        } else {
          EquivalenceClass conjunctClass = eqFactory.of(Conjunction.of(alpha));
          EquivalenceClass formulaClass = eqFactory.of(successor);

          var satisfactionVisitor = new PromisedSetAcceptanceVisitor(promisedSet, true);

          if (conjunctClass.implies(formulaClass)) {
            if (promisedSet.formulaeF().isEmpty()) {
              goodOrNeutral.add(0, promisedSet);
              priority = Integer.min((previousPermutation.size() - promIndex) * 2, priority);
            } else {
              boolean reset = false;
              Formula currentFirstF = promisedSet.formulaeF().get(0);
              Deque<FOperator> deque = new ArrayDeque<>(promisedSet.formulaeF());

              do {
                FOperator fFormula = deque.pollFirst();
                Set<Formula> finalTokens = newMonitorsF.stream().filter(s ->
                  s.formula().equals(fFormula)).findFirst().orElseThrow().finalStates();

                if (Iterables.any(finalTokens, t -> t.accept(satisfactionVisitor))) {
                  deque.addLast(fFormula);
                  if (deque.peekFirst().equals(promisedSet.firstF())) {
                    reset = true;
                  }
                } else {
                  deque.addFirst(fFormula);
                  break;
                }
              } while (!currentFirstF.equals(deque.peekFirst()));

              goodOrNeutral.add(0, PromisedSet.of(promisedSet.formulaeG(),
                Lists.newArrayList(deque), promisedSet.firstF()));

              if (reset) {
                priority = Integer.min((previousPermutation.size() - promIndex) * 2, priority);
              }
            }
          } else {
            goodOrNeutral.add(0, promisedSet);
          }
        }
      }
      currentPermutation.addAll(goodOrNeutral);
      State newState =
        State.of(successor, newMonitorsG, newMonitorsF, priority, currentPermutation);
      return Edge.of(newState, state.priority());
    });
    automaton.initialState(initialState);

    Map<StateReduction, State> reduction = new HashMap<>();
    Map<State, State> reductionMap = new HashMap<>();

    automaton.forEachState(state -> {
      Map<State, ValuationSet> successors = new HashMap<>();
      automaton.labelledEdges(state).forEach(labelledEdge ->
        successors.merge(labelledEdge.edge.successor(), labelledEdge.valuations,
          ValuationSet::union));
      reductionMap.put(state, reduction.computeIfAbsent(StateReduction.of(state, successors),
        k -> state));
    });

    Automaton<State, ParityAcceptance> reducedAutomaton =
      AutomatonFactory.create(factories.vsFactory, reductionMap.get(initialState),
        acceptance, (state, valuation) -> {
          State successor = automaton.successor(state, valuation);
          assert successor != null;
          return Edge.of(reductionMap.get(successor), successor.priority());
        });

    return AutomatonUtil.asMutable(reducedAutomaton);
  }

  static final class FinalStateVisitor extends PropositionalVisitor<Boolean> {
    static final FinalStateVisitor INSTANCE = new FinalStateVisitor();

    private FinalStateVisitor() {}

    @Override
    public Boolean modalOperatorAction(Formula formula) {
      return !(formula instanceof Literal || formula instanceof XOperator);
    }

    @Override
    public Boolean visit(BooleanConstant booleanConstant) {
      return true;
    }

    @Override
    public Boolean visit(Conjunction conjunction) {
      return visitPropositional(conjunction);
    }

    @Override
    public Boolean visit(Disjunction disjunction) {
      return visitPropositional(disjunction);
    }

    private Boolean visitPropositional(PropositionalFormula propositionalFormula) {
      return propositionalFormula.children.stream().allMatch(c -> c.accept(this));
    }
  }

  private abstract static class PromisedSetVisitor
    implements Visitor<Set<Set<UnaryModalOperator>>> {

    @Override
    public Set<Set<UnaryModalOperator>> visit(BooleanConstant booleanConstant) {
      return Set.of(new HashSet<>());
    }

    @Override
    public Set<Set<UnaryModalOperator>> visit(Conjunction conjunction) {
      return conjunction.map(c -> c.accept(this)).reduce((firstSet, secondSet) -> {
        Set<Set<UnaryModalOperator>> innerSets = new HashSet<>();
        firstSet.forEach(f -> secondSet.forEach(s ->
          innerSets.add(new HashSet<>(Sets.union(f, s)))));
        return innerSets;
      }).orElseThrow();
    }

    @Override
    public Set<Set<UnaryModalOperator>> visit(Disjunction disjunction) {
      return disjunction.map(x -> x.accept(this))
        .flatMap(Collection::stream).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<Set<UnaryModalOperator>> visit(GOperator gOperator) {
      Set<Set<UnaryModalOperator>> sets = gOperator.operand.accept(InsideGVisitor.INSTANCE);
      sets.forEach(s -> s.add(gOperator));
      return sets;
    }

    @Override
    public Set<Set<UnaryModalOperator>> visit(Literal literal) {
      return Set.of(new HashSet<>());
    }

    @Override
    public Set<Set<UnaryModalOperator>> visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }

  private static final class OutsideGVisitor extends PromisedSetVisitor {
    static final OutsideGVisitor INSTANCE = new OutsideGVisitor();

    private OutsideGVisitor() {}

    @Override
    public Set<Set<UnaryModalOperator>> visit(FOperator fOperator) {
      return fOperator.operand.accept(this);
    }
  }

  private static final class InsideGVisitor extends PromisedSetVisitor {
    static final InsideGVisitor INSTANCE = new InsideGVisitor();

    private InsideGVisitor() {}

    @Override
    public Set<Set<UnaryModalOperator>> visit(FOperator fOperator) {
      Set<Set<UnaryModalOperator>> sets = fOperator.operand.accept(this);
      sets.forEach(s -> s.add(fOperator));
      return sets;
    }
  }

  private static final class PromisedSetAcceptanceVisitor implements Visitor<Boolean> {
    private final PromisedSet promisedSet;
    private final boolean satisfaction;

    PromisedSetAcceptanceVisitor(PromisedSet promisedSet, boolean satisfaction) {
      this.promisedSet = promisedSet;
      this.satisfaction = satisfaction;
    }

    @Override
    public Boolean visit(Literal literal) {
      return false;
    }

    @Override
    public Boolean visit(XOperator xOperator) {
      return false;
    }

    @Override
    public Boolean visit(BooleanConstant constant) {
      return constant.equals(satisfaction ? BooleanConstant.TRUE : BooleanConstant.FALSE);
    }

    @Override
    public Boolean visit(Conjunction conjunction) {
      return satisfaction
        ? Iterables.all(conjunction.children, x -> x.accept(this))
        : Iterables.any(conjunction.children, x -> x.accept(this));
    }

    @Override
    public Boolean visit(Disjunction disjunction) {
      return satisfaction
        ? Iterables.any(disjunction.children, x -> x.accept(this))
        : Iterables.all(disjunction.children, x -> x.accept(this));
    }

    @Override
    public Boolean visit(FOperator fOperator) {
      return satisfaction == promisedSet.formulaeF().contains(fOperator);
    }

    @Override
    public Boolean visit(GOperator gOperator) {
      return satisfaction == promisedSet.formulaeG().contains(gOperator);
    }
  }
}
