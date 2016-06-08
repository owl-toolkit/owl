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

package omega_automaton.output;

import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import ltl.Collections3;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class DotPrinter implements HOAConsumer {

    private final PrintWriter out;
    private final List<Integer> initialStates;
    private String name;

    public DotPrinter(OutputStream out) {
        this.out = new PrintWriter(out);
        initialStates = new ArrayList<>();
    }

    @Override
    public boolean parserResolvesAliases() {
        return false;
    }

    @Override
    public void notifyHeaderStart(String version) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void setNumberOfStates(int numberOfStates) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void addStartStates(List<Integer> stateConjunction) throws HOAConsumerException {
        initialStates.addAll(stateConjunction);
    }

    @Override
    public void addAlias(String name, BooleanExpression<AtomLabel> labelExpr) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void setAPs(List<String> aps) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void setAcceptanceCondition(int numberOfSets, BooleanExpression<AtomAcceptance> accExpr) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void provideAcceptanceName(String name, List<Object> extraInfo) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void setName(String name) throws HOAConsumerException {
        this.name = name;
    }

    @Override
    public void setTool(String name, String version) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void addProperties(List<String> properties) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void addMiscHeader(String name, List<Object> content) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void notifyBodyStart() throws HOAConsumerException {
        out.println("digraph \"" + name + "\"");
        out.println('{');
    }

    @Override
    public void addState(int id, String info, BooleanExpression<AtomLabel> labelExpr, List<Integer> accSignature) throws HOAConsumerException {
        if (initialStates.contains(id)) {
            out.println("\tnode [shape=oval, label=\"" + info + "\"] \"" + id + "\";");
        } else {
            out.println("\tnode [shape=rectangle, label=\"" + info + "\"] \"" + id + "\";");
        }
    }

    @Override
    public void addEdgeImplicit(int stateId, List<Integer> conjSuccessors, List<Integer> accSignature) throws HOAConsumerException {
        out.println("\t\"" + stateId + "\" -> \"" + Collections3.getElement(conjSuccessors) + "\";");
    }

    @Override
    public void addEdgeWithLabel(int stateId, BooleanExpression<AtomLabel> labelExpr, List<Integer> conjSuccessors, List<Integer> accSignature) throws HOAConsumerException {
        out.println("\t\"" + stateId + "\" -> \"" + Collections3.getElement(conjSuccessors) + "\" [label=\"" + labelExpr.toString() + "\"];");
    }

    @Override
    public void notifyEndOfState(int stateId) throws HOAConsumerException {
        // NOP
    }

    @Override
    public void notifyEnd() throws HOAConsumerException {
        out.println('}');
        out.flush();
    }

    @Override
    public void notifyAbort() {
        out.println("-- ABORT --");
        out.flush();
    }

    @Override
    public void notifyWarning(String warning) throws HOAConsumerException {
        out.print("-- ABORT: ");
        out.print(warning);
        out.println(" --");
        out.flush();
    }
}
