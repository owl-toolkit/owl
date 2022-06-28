/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

package owl.cinterface;

import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.CONSTANT_FALSE;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.CONSTANT_TRUE;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.UNUSED;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.USED;

import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumConstant;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.LtlfParser;
import owl.ltl.rewriter.PushNextThroughPropositionalVisitor;
import owl.ltl.rewriter.SimplifierRepository;
import owl.ltl.visitors.Converter;

@CContext(CInterface.CDirectives.class)
public final class CLabelledFormula {

  private static final String NAMESPACE = "ltl_formula_";

  private static final int MAX_ITERATIONS = 10;

  private CLabelledFormula() {
  }

  @CEntryPoint(
      name = NAMESPACE + "parse",
      documentation = {
          "Parse the given string with the given atomic propositions and return an LTL formula.",
          CInterface.CHAR_TO_STRING,
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle parse(
      IsolateThread thread,
      CCharPointer cFormulaString,
      CCharPointerPointer cAtomicPropositions,
      int cAtomicPropositionsLength) {

    var labelledFormula = LtlParser.parse(
        CTypeConversion.toJavaString(cFormulaString),
        atomicPropositions(cAtomicPropositions, cAtomicPropositionsLength));
    return ObjectHandles.getGlobal().create(labelledFormula);
  }

  @CEntryPoint(
      name = NAMESPACE + "parse_with_finite_semantics",
      documentation = {
          "Parse the given string with the given atomic propositions and return an LTL formula "
              + "with finite semantics.",
          CInterface.CHAR_TO_STRING,
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle create(
      IsolateThread thread,
      CCharPointer cFormulaString,
      CCharPointerPointer cAtomicPropositions,
      int cAtomicPropositionsLength) {

    var labelledFormula = LtlfParser.parseAndTranslateToLtl(
        CTypeConversion.toJavaString(cFormulaString),
        atomicPropositions(cAtomicPropositions, cAtomicPropositionsLength));
    return ObjectHandles.getGlobal().create(labelledFormula);
  }

  private static List<String> atomicPropositions(CCharPointerPointer pointer, int length) {
    var atomicPropositions = Arrays.asList(new String[length]);

    for (int i = 0; i < length; i++) {
      var cAtomicProposition = pointer.read(i);
      atomicPropositions.set(i, CTypeConversion.toJavaString(cAtomicProposition));
    }

    return atomicPropositions;
  }

  @CEnum("owl_atomic_proposition_status")
  public enum AtomicPropositionStatus {

    @CEnumConstant("OWL_AP_CONSTANT_TRUE")
    CONSTANT_TRUE,
    @CEnumConstant("OWL_AP_CONSTANT_FALSE")
    CONSTANT_FALSE,
    @CEnumConstant("OWL_AP_USED")
    USED,
    @CEnumConstant("OWL_AP_UNUSED")
    UNUSED;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native AtomicPropositionStatus fromCValue(int value);
  }

  @CEntryPoint(
      name = NAMESPACE + "simplify",
      documentation = {
          "Simplify the given LTL formula assuming a Game-semantics where atomic propositions less ",
          "than `firstOutputAtomicProposition` is controlled by the environment trying to dissatisfy ",
          "the formula and atomic proposition greater or equal are controlled by the system. The ",
          "status of atomic proposition is written to the passed int pointer using the encoding ",
          "provided by `owl_atomic_proposition_status`",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle simplify(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int firstOutputAtomicProposition,
      CIntPointer cAtomicPropositionStatuses,
      int cAtomicPropositionsStatusesLength) {

    return ObjectHandles.getGlobal().create(
        simplify(
            ObjectHandles.getGlobal().get(cLabelledFormula),
            firstOutputAtomicProposition,
            cAtomicPropositionStatuses,
            cAtomicPropositionsStatusesLength));
  }

  static LabelledFormula simplify(
      LabelledFormula labelledFormula,
      int firstOutputAtomicProposition,
      CIntPointer cAtomicPropositionStatuses,
      int cAtomicPropositionsStatusesLength) {

    int atomicPropositions = labelledFormula.atomicPropositions().size();
    var atomicPropositionStatuses = new AtomicPropositionStatus[atomicPropositions];
    Arrays.fill(atomicPropositionStatuses, UNUSED);

    // Translate to a restricted negation normal form.
    var processedFormula = PushNextThroughPropositionalVisitor
        .apply(labelledFormula).formula().substitute(Formula::nnf);

    Formula oldFormula;
    Formula newFormula = processedFormula;
    int iterations = 0;

    // Iterate simplifier.
    do {
      oldFormula = newFormula;
      var polaritySimplifier = new PolaritySimplifier(
          oldFormula, atomicPropositionStatuses, firstOutputAtomicProposition);
      newFormula = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(
          oldFormula.accept(polaritySimplifier)
      );
      iterations++;
    } while (iterations < MAX_ITERATIONS && !oldFormula.equals(newFormula));

    processedFormula = newFormula;

    // Mark all occurring atomic propositions as USED.
    processedFormula.atomicPropositions(true).stream().forEach(
        atomicProposition -> {
          assert atomicPropositionStatuses[atomicProposition] == UNUSED;
          atomicPropositionStatuses[atomicProposition] = USED;
        }
    );

    // Write the atomic proposition statuses to the specified destination.
    for (int i = 0; i < cAtomicPropositionsStatusesLength; i++) {
      var status = i < atomicPropositions ? atomicPropositionStatuses[i] : UNUSED;
      cAtomicPropositionStatuses.write(i,
          ImageInfo.inImageCode() ? status.getCValue() : status.ordinal());
    }

    return labelledFormula.wrap(processedFormula);
  }

  private static class PolaritySimplifier extends Converter {

    private final AtomicPropositionStatus[] atomicPropositionStatuses;
    private final Set<Literal> singlePolarityInputVariables;
    private final Set<Literal> singlePolarityOutputVariables;

    @SuppressWarnings("PMD")
    private PolaritySimplifier(Formula formula,
        AtomicPropositionStatus[] atomicPropositionStatuses,
        int firstOutputVariable) {
      super(SyntacticFragment.ALL);

      this.atomicPropositionStatuses = atomicPropositionStatuses;

      Set<Literal> atoms = formula.nnf().subformulas(Literal.class);

      singlePolarityInputVariables = atoms.stream()
          .filter(x -> x.getAtom() < firstOutputVariable && !atoms.contains(x.not()))
          .collect(Collectors.toSet());

      singlePolarityOutputVariables = atoms.stream()
          .filter(x -> firstOutputVariable <= x.getAtom() && !atoms.contains(x.not()))
          .collect(Collectors.toSet());
    }

    @Override
    public Formula visit(Literal literal) {
      if (singlePolarityInputVariables.contains(literal)) {
        var constant = literal.isNegated() ? CONSTANT_TRUE : CONSTANT_FALSE;

        assert atomicPropositionStatuses[literal.getAtom()] == UNUSED
            || atomicPropositionStatuses[literal.getAtom()] == constant;

        atomicPropositionStatuses[literal.getAtom()] = constant;
        return BooleanConstant.FALSE;
      }

      if (singlePolarityOutputVariables.contains(literal)) {
        var constant = literal.isNegated() ? CONSTANT_FALSE : CONSTANT_TRUE;

        assert atomicPropositionStatuses[literal.getAtom()] == UNUSED
            || atomicPropositionStatuses[literal.getAtom()] == constant;

        atomicPropositionStatuses[literal.getAtom()] = constant;
        return BooleanConstant.TRUE;
      }

      assert atomicPropositionStatuses[literal.getAtom()] == UNUSED;
      return literal;
    }

    @Override
    public Formula visit(Biconditional biconditional) {
      assert Collections.disjoint(
          biconditional.subformulas(Literal.class),
          Sets.union(singlePolarityInputVariables, singlePolarityOutputVariables));

      return biconditional;
    }
  }
}
