package owl.ltl.parser.spectra;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.WOperator;

public class Spectra {
  private final List<String> inputs;
  private final List<String> outputs;
  private final List<String> variables;
  private final List<Formula> thetaE;
  private final List<Formula> thetaS;
  private final List<Formula> psiE;
  private final List<Formula> psiS;
  private final List<Formula> phiE;
  private final List<Formula> phiS;

  private final Set<String> player1;

  public Spectra(List<String> inputs, List<String> outputs,
                 List<Formula> thetaE, List<Formula> thetaS,
                 List<Formula> psiE, List<Formula> psiS,
                 List<Formula> phiE, List<Formula> phiS) {
    this.inputs = inputs;
    this.outputs = outputs;
    variables = Stream.concat(inputs.stream(), outputs.stream())
      .collect(Collectors.toList());
    this.thetaE = thetaE;
    this.thetaS = thetaS;
    this.psiE = psiE;
    this.psiS = psiS;
    this.phiE = phiE;
    this.phiS = phiS;
    player1 = new HashSet<>(inputs);
  }

  public List<String> getInputs() {
    return inputs;
  }

  public List<String> getOutputs() {
    return outputs;
  }

  public List<Formula> getSafety() {
    return Stream.concat(psiE.stream(), psiS.stream())
      .collect(Collectors.toList());
  }

  public List<LabelledFormula> getLabelledSafety() {
    return Stream.concat(
      psiE.stream().map(this::toLabelledFormula),
      psiS.stream().map(this::toLabelledFormula)
    ).collect(Collectors.toList());
  }

  public Formula toFormula() {
    Formula initialE = Conjunction.of(thetaE);
    Formula initialS = Conjunction.of(thetaS);
    Formula safetyE = Conjunction.of(psiE);
    Formula safetyS = Conjunction.of(psiS);
    Formula livenessE = Conjunction.of(phiE);
    Formula livenessS = Conjunction.of(phiS);

    Formula part1 = Disjunction.of(livenessE.not(), livenessS);
    Formula part2 = Conjunction.of(new GOperator(safetyE), part1);
    Formula part3 = WOperator.of(safetyS, safetyE.not());
    Formula part4 = Conjunction.of(initialS, part3, part2);

    return Disjunction.of(initialE.not(), part4);
  }

  public LabelledFormula toLabelledFormula() {
    return toLabelledFormula(toFormula());
  }

  private LabelledFormula toLabelledFormula(Formula formula) {
    return LabelledFormula.of(formula, variables, player1);
  }
}
