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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CIntPointer;
import owl.ltl.LabelledFormula;

@CContext(CInterface.CDirectives.class)
public final class CDecomposedDPA {

  private static final String NAMESPACE = "decomposed_dpa_";

  private CDecomposedDPA() {}

  @CEntryPoint(
    name = NAMESPACE + "create",
    documentation = {
      "Translate the given formula to a decomposed DPA.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle create(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    boolean simplify,
    boolean monolithic,
    int firstOutputAtomicProposition) {

    var formula = ObjectHandles.getGlobal().<LabelledFormula>get(cLabelledFormula).formula();
    var decomposedDPA = DecomposedDPA.of(
      formula, simplify, monolithic, firstOutputAtomicProposition);
    return ObjectHandles.getGlobal().create(decomposedDPA);
  }

  @CEntryPoint(
    name = NAMESPACE + "automata_size",
    documentation = {
      "Retrieve the number of subautomata."
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
  )
  public static int automataSize(
    IsolateThread thread,
    ObjectHandle cDecomposedDPA) {

    return get(cDecomposedDPA).automata.size();
  }

  @CEntryPoint(
    name = NAMESPACE + "automata_get",
    documentation = {
      "Retrieve the i-th subautomaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle automataGet(
    IsolateThread thread,
    ObjectHandle cDecomposedDPA,
    int index) {

    return ObjectHandles.getGlobal().create(get(cDecomposedDPA).automata.get(index));
  }

  @CEntryPoint(
    name = NAMESPACE + "atomic_proposition_status",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnVariableStatus.class
  )
  public static DecomposedDPA.VariableStatus getAtomicPropositionStatus(
    IsolateThread thread,
    ObjectHandle cDecomposedDPA,
    int atomicProposition) {

    var decomposedDPA = ObjectHandles.getGlobal().<DecomposedDPA>get(cDecomposedDPA);

    if (atomicProposition < decomposedDPA.variableStatuses.size()) {
      return decomposedDPA.variableStatuses.get(atomicProposition);
    } else {
      return DecomposedDPA.VariableStatus.UNUSED;
    }
  }

  @CEntryPoint(
    name = NAMESPACE + "declare_realizability_status",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
  )
  public static boolean declareRealizabilityStatus(
    IsolateThread thread,
    ObjectHandle cDecomposedDPA,
    RealizabilityStatus status,
    CIntPointer cDecomposedState,
    int numberOfStates) {

    var decomposedDPA = ObjectHandles.getGlobal().<DecomposedDPA>get(cDecomposedDPA);
    return decomposedDPA.declare(status, cDecomposedState, numberOfStates);
  }

  @CEntryPoint(
    name = NAMESPACE + "query_realizability_status",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnRealizabilityStatus.class
  )
  public static RealizabilityStatus queryRealizabilityStatus(
    IsolateThread thread,
    ObjectHandle cDecomposedDPA,
    CIntPointer cDecomposedState,
    int numberOfStates) {

    var decomposedDPA = ObjectHandles.getGlobal().<DecomposedDPA>get(cDecomposedDPA);
    return decomposedDPA.query(cDecomposedState, numberOfStates);
  }

  private static DecomposedDPA get(ObjectHandle cDecomposedDPA) {
    return ObjectHandles.getGlobal().get(cDecomposedDPA);
  }

  @CEnum("realizability_status_t")
  public enum RealizabilityStatus {
    REALIZABLE, UNREALIZABLE, UNKNOWN;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native RealizabilityStatus fromCValue(int value);
  }

  @CContext(CInterface.CDirectives.class)
  public static final class Structure {

    private static final String NAMESPACE = "decomposed_dpa_structure_";

    private Structure() {}

    @CEntryPoint(
      name = NAMESPACE + "get",
      documentation = {
        CInterface.CALL_DESTROY
      },
      exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
    )
    public static ObjectHandle createStructureHandle(
      IsolateThread thread,
      ObjectHandle cDecomposedDPA) {

      var decomposedDPA = ObjectHandles.getGlobal().<DecomposedDPA>get(cDecomposedDPA);
      return ObjectHandles.getGlobal().create(decomposedDPA.structure);
    }

    @CEntryPoint(
      name = NAMESPACE + "node_type",
      exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnNodeType.class
    )
    public static NodeType nodeType(
      IsolateThread thread,
      ObjectHandle cLabelledTree) {

      return get(cLabelledTree).label;
    }

    @CEntryPoint(
      name = NAMESPACE + "children",
      exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
    )
    public static int getNumberOfChildren(
      IsolateThread thread,
      ObjectHandle cLabelledTree) {

      return getNode(cLabelledTree).children.size();
    }

    @CEntryPoint(
      name = NAMESPACE + "get_child",
      documentation = {
        CInterface.CALL_DESTROY
      },
      exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
    )
    public static ObjectHandle getChild(
      IsolateThread thread,
      ObjectHandle cLabelledTree,
      int index) {

      return ObjectHandles.getGlobal().create(getNode(cLabelledTree).children.get(index));
    }

    @CEntryPoint(
      name = NAMESPACE + "referenced_automaton",
      exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
    )
    public static int getReferencedAutomaton(
      IsolateThread thread,
      ObjectHandle cLabelledTree) {

      return getLeaf(cLabelledTree).reference.index;
    }

    @CEntryPoint(
      name = NAMESPACE + "referenced_formula",
      documentation = {
        CInterface.CALL_DESTROY
      },
      exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
    )
    public static ObjectHandle createFormulaObjectHandle(
      IsolateThread thread,
      ObjectHandle cLabelledTree) {

      return ObjectHandles.getGlobal().create(getLeaf(cLabelledTree).reference.formula);
    }

    @CEntryPoint(
      name = NAMESPACE + "referenced_alphabet_mapping",
      exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
    )
    public static int alphabetMapping(
      IsolateThread thread,
      ObjectHandle cLabelledTree,
      int i) {

      var mapping = getLeaf(cLabelledTree).reference.alphabetMapping;

      if (i < mapping.length()) {
        int value = mapping.get(i);

        if (value >= 0) {
          return value;
        }
      }

      return -1;
    }

    private static DecomposedDPA.DecomposedDPAStructure get(
      ObjectHandle cDeterministicAutomaton) {
      return ObjectHandles.getGlobal().get(cDeterministicAutomaton);
    }

    private static DecomposedDPA.DecomposedDPAStructure.Leaf getLeaf(
      ObjectHandle cDeterministicAutomaton) {
      return ObjectHandles.getGlobal().get(cDeterministicAutomaton);
    }

    private static DecomposedDPA.DecomposedDPAStructure.Node getNode(
      ObjectHandle cDeterministicAutomaton) {
      return ObjectHandles.getGlobal().get(cDeterministicAutomaton);
    }

    @CEnum("node_type_t")
    public enum NodeType {
      AUTOMATON, BICONDITIONAL, CONJUNCTION, DISJUNCTION;

      @CEnumValue
      public native int getCValue();

      @CEnumLookup
      public static native NodeType fromCValue(int value);
    }
  }
}
