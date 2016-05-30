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

package ltl;


import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RelevantGFormulaeWithSlaveSuspension implements Visitor<Boolean> {

    public static final RelevantGFormulaeWithSlaveSuspension RELEVANT_G_FORMULAE_PRESENT = new RelevantGFormulaeWithSlaveSuspension();

    private RelevantGFormulaeWithSlaveSuspension() {
        super();
    }

    @Override
    public Boolean defaultAction(Formula formula) {
        return false;
    }

    @Override
    public Boolean visit(GOperator g) {
        return true;
    }

    @Override
    public Boolean visit(FOperator f) {
        return f.operand.accept(this);
    }

    @Override
    public Boolean visit(UOperator u) {
        return u.left.accept(this) || u.right.accept(this);
    }

    @Override
    public Boolean visit(Conjunction c) {
        Set<Formula> canBeWaited = c.children.stream().filter(child -> !child.accept(new ContainsVisitor(GOperator.class))).collect(Collectors.toSet());
        c.children.stream().filter(child -> child.accept(new PatientSlaveVisitor())).forEach(canBeWaited::add);
        Set<Formula> relevantChildren = new HashSet<>(c.children);
        relevantChildren.removeIf(child -> child.accept(new PatientSlaveVisitor()));

        if (!canBeWaited.isEmpty()) {
            relevantChildren = relevantChildren.stream().filter(child -> !child.isSuspendable())
                    .collect(Collectors.toSet());
        }

        return relevantChildren.stream().anyMatch(child -> child.accept(this));
    }

    @Override
    public Boolean visit(Disjunction d) {
        Set<Formula> patientFormulae = d.children.stream().filter(child -> child.accept(new PatientSlaveVisitor()))
                .collect(Collectors.toSet());

        Set<Formula> relevantChildren = new HashSet<>(d.children);
        relevantChildren.removeIf(child -> child.accept(new PatientSlaveVisitor()));

        if (!patientFormulae.isEmpty()) {
            relevantChildren = relevantChildren.stream().filter(child -> !child.isSuspendable())
                    .collect(Collectors.toSet());
        }

        return relevantChildren.stream().anyMatch(child -> child.accept(this));
    }
}
