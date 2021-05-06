package owl.automaton.symbolic;

import static owl.automaton.symbolic.StatisticsCollector.STATISTICS_COLLECTOR;
import static owl.automaton.symbolic.VariableAllocation.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.VariableAllocation.VariableType.COLOUR;
import static owl.automaton.symbolic.VariableAllocation.VariableType.STATE;
import static owl.automaton.symbolic.VariableAllocation.VariableType.SUCCESSOR_STATE;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.bdd.BddSet;
import owl.bdd.MtBdd;
import owl.collections.BitSet2;

public final class AigerWriter {

  private static final Object TERMINAL = new Object();

  private AigerWriter() {}

  private static void writeLiteral(StringBuilder builder, int literal) {
    builder
      .append(literal)
      .append('\n');
  }

  private static void writeLiterals(StringBuilder builder, int numberOfLiterals, int offset) {
    for (int i = 2 * offset; i < 2 * (offset + numberOfLiterals); i += 2) {
      writeLiteral(builder, i);
    }
  }

  private static void writeLatch(StringBuilder builder, int latch, int nextState) {
    builder
      .append(latch)
      .append(' ')
      .append(nextState)
      .append('\n');
  }

  private static void writeLatches(
    StringBuilder builder,
    int numberOfLatches,
    int latchOffset,
    int nextStateOffset
  ) {
    for (int i = 2 * latchOffset; i < 2 * (latchOffset + numberOfLatches); i += 2) {
      writeLatch(builder, i, i + 2 * nextStateOffset);
    }
  }

  private static void writeResetLatch(StringBuilder builder, int at) {
    builder
      .append(2 * at)
      .append(" 1\n");
  }

  private static void writeResetGates(
    StringBuilder builder,
    BitSet initial,
    int offset,
    int resetLatch,
    int currentStateOffset
  ) {
    int resetGateCounter = 0;
    for (int i = initial.nextSetBit(0); i != -1; i = initial.nextSetBit(i + 1)) {
      writeGate(
        builder,
        offset + resetGateCounter++,
        2 * resetLatch,
        2 * (currentStateOffset + i) + 1
      );
    }
  }

  private static void writeGate(StringBuilder builder, int gateNumber, int arg0, int arg1) {
    builder
      .append(2 * gateNumber)
      .append(' ')
      .append(arg0)
      .append(' ')
      .append(arg1)
      .append('\n');
  }


  private static List<BddSet> getOutputFunctions(
    BddSet strategy,
    BitSet quantifiedVariables,
    IntStream outputs
  ) {
    return outputs.mapToObj(i -> getOutputFunction(strategy, quantifiedVariables, i)
    ).collect(Collectors.toUnmodifiableList());
  }

  private static BddSet getOutputFunction(
    BddSet strategy,
    BitSet quantifiedVariables,
    int variable
  ) {
    BitSet restriction = new BitSet();
    restriction.set(variable);
    return strategy.restrict(restriction, restriction).project(quantifiedVariables);
  }

  private static int variableInAig(
    VariableAllocation allocation,
    int variable,
    BitSet initial,
    BitSet controlledAPs,
    int resetGateOffset,
    int latchOffset
  ) {
    switch (allocation.typeOf(variable)) {
      case STATE:
        int localState = allocation.globalToLocal(variable);
        if (initial.get(localState)) {
          BitSet initialLocal = BitSet2.copyOf(initial);
          initialLocal.set(localState, allocation.variables(STATE).size(), false);
          return 2 * (resetGateOffset + initialLocal.cardinality()) + 1;
        } else {
          return 2 * (allocation.globalToLocal(variable) + latchOffset);
        }
      case ATOMIC_PROPOSITION:
        // Assert that this is an input AP.
        assert !controlledAPs.get(allocation.globalToLocal(variable));
        int apLocal = allocation.globalToLocal(variable);
        BitSet local = new BitSet();
        local.set(0, apLocal);
        local.andNot(controlledAPs);
        return 2 * (local.cardinality() + 1);
      default:
        throw new AssertionError("Unreachable");
    }
  }

  private static void writeOutputSymbol(StringBuilder builder, int number, String label) {
    writeSymbol(builder, number, label, true);
  }

  private static void writeInputSymbol(StringBuilder builder, int number, String label) {
    writeSymbol(builder, number, label, false);
  }

  private static void writeSymbol(
    StringBuilder builder,
    int number,
    String label,
    boolean isOutput
  ) {
    builder
      .append(isOutput ? 'o' : 'i')
      .append(number)
      .append(' ')
      .append(label)
      .append('\n');
  }

  private static void writeSymbolTable(
    StringBuilder builder,
    BitSet controlledAPs,
    List<String> aps
  ) {
    int inputCounter = 0;
    int outputCounter = 0;
    for (int i = 0; i < aps.size(); i++) {
      if (controlledAPs.get(i)) {
        writeOutputSymbol(builder, outputCounter++, aps.get(i));
      } else {
        writeInputSymbol(builder, inputCounter++, aps.get(i));
      }
    }
  }

  private static int visit(
    StringBuilder builder,
    Map<MtBdd<?>, Integer> nodeToGates,
    int[] offset,
    VariableAllocation allocation,
    BitSet initial,
    BitSet controlledAPs,
    int resetGateOffset,
    int latchOffset,
    MtBdd<?> nodeOrLeaf
  ) {
    if (nodeToGates.containsKey(nodeOrLeaf)) {
      return nodeToGates.get(nodeOrLeaf);
    }
    if (nodeOrLeaf instanceof MtBdd.Leaf) {
      return ((MtBdd.Leaf<?>) nodeOrLeaf).value.isEmpty() ? 0 : 1;
    }
    MtBdd.Node<?> node = (MtBdd.Node<?>) nodeOrLeaf;
    // Proceed recursively, obtaining the gates that represent the low and high node
    int low = visit(
      builder,
      nodeToGates,
      offset,
      allocation,
      initial,
      controlledAPs,
      resetGateOffset,
      latchOffset,
      node.falseChild
    );
    int high = visit(
      builder,
      nodeToGates,
      offset,
      allocation,
      initial,
      controlledAPs,
      resetGateOffset,
      latchOffset,
      node.trueChild
    );

    int variable = variableInAig(
      allocation,
      node.variable,
      initial,
      controlledAPs,
      resetGateOffset,
      latchOffset
    );

    // Write gates for current node
    int gate;
    if (low == 0 && high == 0) {
      // false
      gate = 0;
    } else if (low == 0 && high == 1) {
      // variable
      gate = variable;
    } else if (low == 1 && high == 0) {
      // !variable
      gate = variable + ((variable & 1) == 1 ? -1 : 1);
    } else if (low == 1 && high == 1) {
      // true
      gate = 1;
    } else if (low == 0) {
      // variable && high
      writeGate(builder, offset[0], variable, high);
      gate = 2 * offset[0];
      offset[0]++;
    } else if (low == 1) {
      // !(variable && !(variable && high))
      int gate1 = offset[0];
      int gate2 = offset[0] + 1;
      gate = 2 * gate2 + 1;
      offset[0] += 2;
      writeGate(builder, gate1, variable, high);
      writeGate(builder, gate2, variable, 2 * gate1 + 1);
    } else if (high == 0) {
      // !variable && low
      writeGate(builder, offset[0], variable + ((variable & 1) == 1 ? -1 : 1), low);
      gate = 2 * offset[0];
      offset[0]++;
    } else if (high == 1) {
      // !(!(!variable && low) && !variable)
      int gate1 = offset[0];
      int gate2 = offset[0] + 1;
      gate = 2 * gate2 + 1;
      offset[0] += 2;
      writeGate(builder, gate1, variable + ((variable & 1) == 1 ? -1 : 1), low);
      writeGate(builder, gate2, 2 * gate1 + 1, variable + ((variable & 1) == 1 ? -1 : 1));
    } else {
      // !(!(!variable && low) && !(variable && high))
      int lowPart = offset[0];
      int highPart = offset[0] + 1;
      int combined = offset[0] + 2;
      gate = 2 * combined + 1;
      offset[0] += 3;
      writeGate(builder, lowPart, variable + ((variable & 1) == 1 ? -1 : 1), low);
      writeGate(builder, highPart, variable, high);
      writeGate(builder, combined, 2 * lowPart + 1, 2 * highPart + 1);
    }
    nodeToGates.put(node, gate);
    return gate;
  }

  private static int writeStrategyGates(
    BddSet strategy,
    VariableAllocation allocation,
    BitSet controlledAPs,
    int numberOfInputs,
    int numberOfAps,
    int numberOfLatches,
    BitSet initial,
    StringBuilder builder
  ) {
    BitSet quantifiedVariables = allocation.variables(SUCCESSOR_STATE, COLOUR)
      .copyInto(new BitSet());
    quantifiedVariables.or(allocation.localToGlobal(controlledAPs, ATOMIC_PROPOSITION));

    List<BddSet> nextStateFunctions = getOutputFunctions(strategy, quantifiedVariables,
      allocation.variables(SUCCESSOR_STATE).intStream());
    List<BddSet> outputFunctions = getOutputFunctions(strategy, quantifiedVariables,
      allocation.localToGlobal(controlledAPs, ATOMIC_PROPOSITION).stream());

    // Prepare for writing gates
    Map<MtBdd<?>, Integer> nodeToGates = new HashMap<>();
    int[] counter = {numberOfAps + 2 * numberOfLatches + 2 + initial
      .cardinality()}; // Wrap counter in array for pass-by-reference

    // Write next state function gates
    for (int i = 0; i < nextStateFunctions.size(); i++) {
      BddSet nextStateFunction = nextStateFunctions.get(i);
      int latchNext = numberOfAps + numberOfLatches + i + 2;
      int rootGate = visit(
        builder,
        nodeToGates,
        counter,
        allocation,
        initial,
        controlledAPs,
        numberOfAps + 2 * numberOfLatches + 2,
        numberOfInputs + 1,
        nextStateFunction.toMtBdd(TERMINAL)
      );
      writeGate(builder, latchNext, rootGate, rootGate);
    }

    // Write output function gates
    for (int i = 0; i < outputFunctions.size(); i++) {
      BddSet outputFunction = outputFunctions.get(i);
      int output = numberOfInputs + numberOfLatches + 2 + i;
      int rootGate = visit(
        builder,
        nodeToGates,
        counter,
        allocation,
        initial,
        controlledAPs,
        numberOfAps + 2 * numberOfLatches + 2,
        numberOfInputs + 1,
        outputFunction.toMtBdd(TERMINAL)
      );
      writeGate(builder, output, rootGate, rootGate);
    }
    return counter[0];
  }


  public static String toAiger(
    BddSet strategy,
    VariableAllocation allocation,
    BitSet controlledAPs,
    List<String> aps,
    BitSet initial
  ) {
    assert isDeterministic(strategy, allocation, controlledAPs);

    int numberOfAps = allocation.variables(ATOMIC_PROPOSITION).size();
    int numberOfOutputs = controlledAPs.cardinality();
    int numberOfInputs = numberOfAps - numberOfOutputs;
    int numberOfLatches = allocation.variables(STATE).size();

    StringBuilder builder = new StringBuilder();

    writeLiterals(builder, numberOfInputs, 1);
    writeLatches(
      builder,
      numberOfLatches,
      numberOfInputs + 1,
      numberOfLatches + numberOfOutputs + 1
    );
    writeResetLatch(builder, numberOfInputs + numberOfLatches + 1);
    writeLiterals(builder, numberOfOutputs, numberOfInputs + numberOfLatches + 2);
    writeResetGates(
      builder,
      initial,
      numberOfAps + 2 * numberOfLatches + 2,
      numberOfInputs + numberOfLatches + 1,
      numberOfInputs + 1
    );

    int maxIndex = writeStrategyGates(
      strategy,
      allocation,
      controlledAPs,
      numberOfInputs,
      numberOfAps,
      numberOfLatches,
      initial,
      builder
    );

    writeSymbolTable(builder, controlledAPs, aps);

    // Write header
    builder.insert(0, String.format("aag %d %d %d %d %d\n",
      maxIndex - 1,
      numberOfInputs,
      numberOfLatches + 1,
      numberOfOutputs,
      maxIndex - numberOfInputs - numberOfLatches - 2
      )
    );
    STATISTICS_COLLECTOR.stop(maxIndex - numberOfInputs - numberOfLatches - 2);
    return builder.toString();
  }

  private static boolean isDeterministic(
    BddSet strategy,
    VariableAllocation allocation,
    BitSet controlledAPs
  ) {
    BitSet vars = allocation.variables(STATE, ATOMIC_PROPOSITION).copyInto(new BitSet());
    vars.andNot(allocation.localToGlobal(controlledAPs, ATOMIC_PROPOSITION));
    for (var inputs : BitSet2.powerSet(vars)) {
      var it = strategy.intersection(strategy.factory().of(inputs, vars))
        .iterator(allocation.variables(VariableAllocation.VariableType.values()));
      if (it.hasNext()) {
        it.next();
      }
      if (it.hasNext()) {
        return false;
      }
    }
    return true;
  }


}
