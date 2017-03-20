/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.output;

import com.google.common.collect.Iterables;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;

public final class DotPrinter implements HOAConsumer {
  private final Set<Integer> initialStates;
  private final PrintWriter out;
  private String name = "";

  public DotPrinter(PrintWriter out) {
    this.out = out;
    initialStates = new HashSet<>();
  }

  @Override
  public void addAlias(String name, BooleanExpression<AtomLabel> labelExpr) {
    // NOP
  }

  @Override
  public void addEdgeImplicit(int stateId, List<Integer> conjSuccessors,
    List<Integer> accSignature) {
    out.println("\t\"" + stateId + "\" -> \"" + Iterables.getOnlyElement(conjSuccessors) + "\";");
  }

  @Override
  public void addEdgeWithLabel(int stateId, BooleanExpression<AtomLabel> labelExpr,
    List<Integer> conjSuccessors, List<Integer> accSignature) {
    out.println(
      "\t\"" + stateId + "\" -> \"" + Iterables.getOnlyElement(conjSuccessors) + "\" [label=\""
        + labelExpr + "\"];");
  }

  @Override
  public void addMiscHeader(String name, List<Object> content) {
    // NOP
  }

  @Override
  public void addProperties(List<String> properties) {
    // NOP
  }

  @Override
  public void addStartStates(List<Integer> stateConjunction) {
    initialStates.addAll(stateConjunction);
  }

  @Override
  public void addState(int id, String info, BooleanExpression<AtomLabel> labelExpr,
    List<Integer> accSignature) {
    if (initialStates.contains(id)) {
      out.println("\tnode [shape=oval, label=\"" + info + "\"] \"" + id + "\";");
    } else {
      out.println("\tnode [shape=rectangle, label=\"" + info + "\"] \"" + id + "\";");
    }
  }

  @Override
  public void notifyAbort() {
    out.println("-- ABORT --");
    out.flush();
  }

  @Override
  public void notifyBodyStart() {
    out.print("digraph \"");
    out.print(name);
    out.print('"');
    out.println('{');
  }

  @Override
  public void notifyEnd() {
    out.println('}');
    out.flush();
  }

  @Override
  public void notifyEndOfState(int stateId) {
    // NOP
  }

  @Override
  public void notifyHeaderStart(String version) {
    // NOP
  }

  @Override
  public void notifyWarning(String warning) {
    out.print("-- ABORT: ");
    out.print(warning);
    out.println(" --");
    out.flush();
  }

  @SuppressWarnings("MethodReturnAlwaysConstant")
  @Override
  public boolean parserResolvesAliases() {
    return false;
  }

  @Override
  public void provideAcceptanceName(String name, List<Object> extraInfo) {
    // NOP
  }

  @Override
  public void setAPs(List<String> aps) {
    // NOP
  }

  @Override
  public void setAcceptanceCondition(int numberOfSets, BooleanExpression<AtomAcceptance> accExpr) {
    // NOP
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void setNumberOfStates(int numberOfStates) {
    // NOP
  }

  @Override
  public void setTool(String name, String version) {
    // NOP
  }
}
