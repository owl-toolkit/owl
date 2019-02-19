package owl.ltl.spectra;

import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.immutables.value.Value;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.OOperator;

@Value.Immutable
public abstract class Spectra {

  public abstract String title();

  public abstract BitSet inputs();

  public abstract BitSet outputs();

  public abstract List<String> variables();

  @Value.Default
  public List<Formula> thetaE() {
    return List.of();
  }

  @Value.Default
  public List<Formula> thetaS() {
    return List.of();
  }

  @Value.Default
  public List<Formula> psiE() {
    return List.of();
  }

  @Value.Default
  public List<Formula> psiS() {
    return List.of();
  }

  @Value.Default
  public List<Formula> phiE() {
    return List.of();
  }

  @Value.Default
  public List<Formula> phiS() {
    return List.of();
  }

  @Value.Derived
  public int numberOfInputs() {
    return inputs().cardinality();
  }

  @Value.Derived
  public LabelledFormula toFormula() {
    return toFormula(
      Conjunction.of(thetaE()),
      Conjunction.of(thetaS()),
      Conjunction.of(psiE()),
      Conjunction.of(psiS()),
      Conjunction.of(phiE().stream().map(x -> GOperator.of(FOperator.of(x)))),
      Conjunction.of(phiS().stream().map(x -> GOperator.of(FOperator.of(x))))
    );
  }

  private LabelledFormula toFormula(Formula initialE, Formula initialS, Formula safetyE,
    Formula safetyS, Formula livenessE, Formula livenessS) {

    Formula part1 = Conjunction.of(GOperator.of(safetyE), livenessE);
    Formula part2 = Disjunction.of(part1.not(), livenessS);
    Formula part3 = GOperator.of(
      Disjunction.of(OOperator.of(safetyE.not()), safetyS)
    );
    Formula part4 = Conjunction.of(initialS, part3, part2);

    return toLabelledFormula(Disjunction.of(initialE.not(), part4));
  }


  public List<LabelledFormula> getSafety() {
    return Stream.concat(
      psiE().stream().map(x -> toLabelledFormula(new GOperator(x))),
      psiS().stream().map(x -> toLabelledFormula(new GOperator(x)))
    ).collect(Collectors.toList());
  }
  /*
  public List<LabelledFormula> getSafety() {
    return Stream.concat(
      psiE().stream().map(this::toLabelledFormula),
      psiS().stream().map(this::toLabelledFormula)
    ).collect(Collectors.toList());
  }
  */

  private LabelledFormula toLabelledFormula(Formula formula) {
    return LabelledFormula.of(formula, variables(), inputs());
  }
}
