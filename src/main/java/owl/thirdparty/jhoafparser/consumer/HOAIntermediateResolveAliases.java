//==============================================================================
//
//	Copyright (c) 2014-
//	Authors:
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de>
//	* David Mueller <david.mueller@tcs.inf.tu-dresden.de>
//
//------------------------------------------------------------------------------
//
//	This file is part of the jhoafparser library, http://automata.tools/hoa/jhoafparser/
//
//	The jhoafparser library is free software; you can redistribute it and/or
//	modify it under the terms of the GNU Lesser General Public
//	License as published by the Free Software Foundation; either
//	version 2.1 of the License, or (at your option) any later version.
//
//	The jhoafparser library is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//	Lesser General Public License for more details.
//
//	You should have received a copy of the GNU Lesser General Public
//	License along with this library; if not, write to the Free Software
//	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
//
//==============================================================================

package owl.thirdparty.jhoafparser.consumer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import owl.automaton.hoa.HoaWriter;
import owl.logic.propositional.PropositionalFormula;
import owl.thirdparty.jhoafparser.ast.AtomLabel;

/**
 * A {@code HOAIntermediate} that resolves aliases on-the-fly.
 * <p>
 * Stores the definition of aliases from the header and resolves
 * any aliases in label expressions before passing the events to
 * the next consumer.
 * <p>
 */
public class HOAIntermediateResolveAliases extends HOAIntermediate
{
	/** The map storing alias definitions */
	HashMap<String, PropositionalFormula<AtomLabel>> aliases = new HashMap<>();

	/** Constructor, with next consumer */
	public HOAIntermediateResolveAliases(HOAConsumer next)
	{
		super(next);
	}

	/** Returns true if {@code labelExpr} contains any unresolved aliases */
	private static boolean containsAliases(PropositionalFormula<AtomLabel> labelExpr) {
    return labelExpr.variables().stream().anyMatch(AtomLabel::isAlias);
	}

	/**
	 * Check that all aliases used in {@code labelExpr} are defined,
	 * otherwise throw {@code HOAConsumerException}
	 **/
	private void checkAliasDefinedness(PropositionalFormula<AtomLabel> labelExpr) throws HOAConsumerException {
    for (var atom : labelExpr.variables()) {
      if (atom.isAlias()) {
        String aliasName = atom.aliasName();
        if (!aliases.containsKey(aliasName)) {
          throw new HOAConsumerException("Expression "+labelExpr+" uses undefined alias @"+aliasName);
        }
      }
    }
	}

	/**
	 * Return a label expression that is obtained by replacing all aliases from the argument expression.
	 **/
	private PropositionalFormula<AtomLabel> resolveAliases(PropositionalFormula<AtomLabel> labelExpr)
	{
		return labelExpr.substitute(atom -> {
			if (!atom.isAlias()) {
				return PropositionalFormula.Variable.of(atom);
			} else {
				PropositionalFormula<AtomLabel> resolved = aliases.get(atom.aliasName());
				if (resolved == null) {
					throw new HoaWriter.UncheckedHoaConsumerException(
            new HOAConsumerException("Can not resolve alias @"+ atom.aliasName()));
				}
				if (containsAliases(resolved)) {
					resolved = resolveAliases(resolved);
				}
				return resolved;
			}
    });
	}

	@Override
	public void addAlias(String name, PropositionalFormula<AtomLabel> labelExpr) throws HOAConsumerException
	{
		if (aliases.containsKey(name)) {
			throw new HOAConsumerException("Alias "+name+" is defined multiple times!");
		}

		if (containsAliases(labelExpr)) {
			// check that all the aliases in the expression are already defined
			checkAliasDefinedness(labelExpr);

			// resolve aliases in the expression
			labelExpr = resolveAliases(labelExpr);
		}

		aliases.put(name, labelExpr);
	}

	@Override
	public void addState(int id, String info, PropositionalFormula<AtomLabel> labelExpr, List<Integer> accSignature) throws HOAConsumerException
	{
		if (labelExpr != null && containsAliases(labelExpr)) {
			labelExpr = resolveAliases(labelExpr);
		}
		next.addState(id, info, labelExpr, accSignature);
	}

	@Override
	public void addEdgeWithLabel(int stateId, PropositionalFormula<AtomLabel> labelExpr, Collection<Integer> conjSuccessors, Collection<Integer> accSignature)
			throws HOAConsumerException
	{
		if (labelExpr != null && containsAliases(labelExpr)) {
			labelExpr = resolveAliases(labelExpr);
		}

		next.addEdgeWithLabel(stateId, labelExpr, conjSuccessors, accSignature);
	}
}
