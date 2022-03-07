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

package owl.thirdparty.jhoafparser.transformations;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import java.util.ArrayList;
import java.util.List;
import owl.logic.propositional.PropositionalFormula;
import owl.thirdparty.jhoafparser.ast.AtomLabel;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerException;
import owl.thirdparty.jhoafparser.storage.StoredAutomaton;
import owl.thirdparty.jhoafparser.storage.StoredAutomatonManipulator;
import owl.thirdparty.jhoafparser.storage.StoredEdgeImplicit;
import owl.thirdparty.jhoafparser.storage.StoredEdgeWithLabel;
import owl.thirdparty.jhoafparser.util.ImplicitEdgeHelper;

/** A StoredAutomatonManipulator that converts implicit to explicit labels */
public class ToExplicitLabels implements StoredAutomatonManipulator {
  /** The (pre-computed) label expression for each implicit index */
	private List<PropositionalFormula<AtomLabel>> explicitForImplicit;

	/** Precompute the label expressions for each implicit index */
	private void prepareExpressions(ImplicitEdgeHelper implicitHelper, StoredAutomaton aut) throws HOAConsumerException {
		 explicitForImplicit = new ArrayList<>((int) implicitHelper.getEdgesPerState());

		 if (implicitHelper.getEdgesPerState() > Integer.MAX_VALUE) {
			 throw new HOAConsumerException("Too many atomic propositions...");
		 }

		 Interner<PropositionalFormula<AtomLabel>> uniqueTable = Interners.newStrongInterner();

		 for (long i = 0; i < implicitHelper.getEdgesPerState(); i++) {
       explicitForImplicit.add(implicitHelper.toExplicitLabel(i, uniqueTable));
     }
	}

  @Override
	public StoredAutomaton manipulate(StoredAutomaton aut) throws HOAConsumerException
	{
    ImplicitEdgeHelper implicitHelper = new ImplicitEdgeHelper(aut.getStoredHeader().getAPs().size());

    for (int stateId = 0; stateId <= aut.getHighestStateIndex(); stateId++) {
      if (aut.hasEdgesImplicit(stateId)) {
        if (explicitForImplicit == null) prepareExpressions(implicitHelper, aut);
        int implicitEdge = 0;
        for (StoredEdgeImplicit edge : aut.getEdgesImplicit(stateId)) {
          aut.addEdgeWithLabel(stateId, new StoredEdgeWithLabel(explicitForImplicit.get(implicitEdge), edge.getConjSuccessors(), edge.getAccSignature()));
          implicitEdge++;
        }
        aut.clearEdgesImplicit(stateId);
      }
    }

    return aut;
	}
}
