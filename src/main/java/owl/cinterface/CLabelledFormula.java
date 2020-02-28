/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import java.util.Arrays;
import java.util.List;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import owl.ltl.ltlf.LtlfParser;
import owl.ltl.parser.LtlParser;

@CContext(CInterface.CDirectives.class)
public final class CLabelledFormula {

  private static final String NAMESPACE = "ltl_formula_";

  private CLabelledFormula() {}

  @CEntryPoint(
    name = NAMESPACE + "parse",
    documentation = {
      "Parse the given string with the given atomic propositions and return an LTL formula.",
      CInterface.CHAR_TO_STRING,
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
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
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle create(
    IsolateThread thread,
    CCharPointer cFormulaString,
    CCharPointerPointer cAtomicPropositions,
    int cAtomicPropositionsLength) {

    var labelledFormula = LtlfParser.parse(
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
}
