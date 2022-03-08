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
import java.util.List;
import owl.logic.propositional.PropositionalFormula;
import owl.thirdparty.jhoafparser.ast.AtomLabel;

/**
 * The {@code HOAIntermediate} class provides a mechanism to chain
 * multiple {@code HOAConsumer} together.
 * Implementing the {@code HOAConsumer} interface, the default behavior
 * is to simply propagate method calls to the {@code next} consumer.
 * <p>
 * By overriding methods, this behavior can be customized, e.g.,
 * validating constraints on the input ({@link HOAIntermediateCheckValidity}),
 * performing on-the-fly transformations ({@link HOAIntermediateResolveAliases}).
 */
public class HOAIntermediate implements HOAConsumer
{
	/** The next consumer. */
	protected HOAConsumer next;

	/** Constructor, providing the next consumer */
	public HOAIntermediate(HOAConsumer next)
	{
		this.next = next;
	}

	@Override
	public boolean parserResolvesAliases()
	{
		return next.parserResolvesAliases();
	}

	@Override
	public void notifyHeaderStart(String version) throws HOAConsumerException
	{
		next.notifyHeaderStart(version);
	}

	@Override
	public void setNumberOfStates(int numberOfStates) throws HOAConsumerException
	{
		next.setNumberOfStates(numberOfStates);
	}

	@Override
	public void addStartStates(List<Integer> stateConjunction) throws HOAConsumerException
	{
		next.addStartStates(stateConjunction);
	}

	@Override
	public void addAlias(String name, PropositionalFormula<AtomLabel> labelExpr) throws HOAConsumerException
	{
		next.addAlias(name, labelExpr);
	}

	@Override
	public void setAPs(List<String> aps) throws HOAConsumerException
	{
		next.setAPs(aps);
	}

	@Override
	public void setAcceptanceCondition(int numberOfSets, PropositionalFormula<Integer> accExpr) throws HOAConsumerException
	{
		next.setAcceptanceCondition(numberOfSets, accExpr);
	}

	@Override
	public void provideAcceptanceName(String name, List<Object> extraInfo) throws HOAConsumerException
	{
		next.provideAcceptanceName(name,  extraInfo);
	}

	@Override
	public void setName(String name) throws HOAConsumerException
	{
		next.setName(name);

	}

	@Override
	public void setTool(String name, String version) throws HOAConsumerException
	{
		next.setTool(name, version);
	}

	@Override
	public void addProperties(List<String> properties) throws HOAConsumerException
	{
		next.addProperties(properties);
	}

	@Override
	public void addMiscHeader(String name, List<Object> content) throws HOAConsumerException
	{
		next.addMiscHeader(name, content);

	}

	@Override
	public void notifyBodyStart() throws HOAConsumerException
	{
		next.notifyBodyStart();
	}

	@Override
	public void addState(int id, String info, PropositionalFormula<AtomLabel> labelExpr, List<Integer> accSignature) throws HOAConsumerException
	{
		next.addState(id, info, labelExpr, accSignature);
	}

	@Override
	public void addEdgeImplicit(int stateId, Collection<Integer> conjSuccessors, Collection<Integer> accSignature) throws HOAConsumerException
	{
		next.addEdgeImplicit(stateId, conjSuccessors, accSignature);
	}

	@Override
	public void addEdgeWithLabel(int stateId, PropositionalFormula<AtomLabel> labelExpr, Collection<Integer> conjSuccessors, Collection<Integer> accSignature)
			throws HOAConsumerException
	{
		next.addEdgeWithLabel(stateId, labelExpr, conjSuccessors, accSignature);
	}

	@Override
	public void notifyEndOfState(int stateId) throws HOAConsumerException
	{
		next.notifyEndOfState(stateId);
	}

	@Override
	public void notifyEnd() throws HOAConsumerException
	{
		next.notifyEnd();
	}

	@Override
	public void notifyAbort()
	{
		next.notifyAbort();
	}

	@Override
	public void notifyWarning(String warning) throws HOAConsumerException
	{
		next.notifyWarning(warning);
	}



}
