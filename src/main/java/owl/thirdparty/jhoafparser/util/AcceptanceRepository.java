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

package owl.thirdparty.jhoafparser.util;

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;
import static owl.logic.propositional.PropositionalFormula.falseConstant;
import static owl.logic.propositional.PropositionalFormula.trueConstant;

import java.util.ArrayList;
import java.util.List;
import owl.logic.propositional.PropositionalFormula;
import owl.thirdparty.jhoafparser.ast.AtomAcceptance;


/**
 * An acceptance repository with all the acceptance conditions specified
 * in the format specification.
 */
public class AcceptanceRepository {
	// identifiers for the various standard acceptance conditions
	private static final String ACC_ALL = "all";
	private static final String ACC_NONE = "none";

	private static final String ACC_BUCHI = "Buchi";
	private static final String ACC_COBUCHI = "coBuchi";

	private static final String ACC_GENERALIZED_BUCHI = "generalized-Buchi";
	private static final String ACC_GENERALIZED_COBUCHI = "generalized-coBuchi";

	private static final String ACC_RABIN = "Rabin";
	private static final String ACC_STREETT = "Streett";

	private static final String ACC_GENERALIZED_RABIN = "generalized-Rabin";

	private static final String ACC_PARITY = "parity";

  /**
     * For a given acc-name header, construct the corresponding canonical acceptance expression.
     * If the acc-name is not known, return {@code null}.
     *
     * @param accName the acceptance name, as passed in an acc-name header
     * @param extraInfo extra info, as passed in an acc-name header
     * @return the canonical acceptance expression for this name, {@code null} if not known
     * @throws IllegalArgumentException if the acceptance name is known, but there is an error with the extraInfo
     */
  public static PropositionalFormula<Integer> getCanonicalAcceptanceExpression(
    String accName, List<Object> extraInfo) throws IllegalArgumentException {

    return switch (accName) {
      case ACC_ALL -> forAll(extraInfo);
      case ACC_NONE -> forNone(extraInfo);
      case ACC_BUCHI -> forBuchi(extraInfo);
      case ACC_COBUCHI -> forCoBuchi(extraInfo);
      case ACC_GENERALIZED_BUCHI -> forGenBuchi(extraInfo);
      case ACC_GENERALIZED_COBUCHI -> forGenCoBuchi(extraInfo);
      case ACC_RABIN -> forRabin(extraInfo);
      case ACC_STREETT -> forStreett(extraInfo);
      case ACC_GENERALIZED_RABIN -> forGeneralizedRabin(extraInfo);
      case ACC_PARITY -> forParity(extraInfo);
      default -> null;
    };
  }

	/** Get canonical for 'all' */
	public static PropositionalFormula<Integer> forAll(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_ALL, extraInfo, 0);

		return trueConstant();
	}

	/** Get canonical for 'none' */
	public static PropositionalFormula<Integer> forNone(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_NONE, extraInfo, 0);

		return falseConstant();
	}

	/** Get canonical for 'Buchi' */
	public static PropositionalFormula<Integer> forBuchi(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_BUCHI, extraInfo, 0);

		return Variable.of(0);
	}

	/** Get canonical for 'coBuchi' */
	public static PropositionalFormula<Integer> forCoBuchi(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_COBUCHI, extraInfo, 0);

		return Negation.of(Variable.of(0));
	}

	/** Get canonical for 'generalized-Buchi' */
	public static PropositionalFormula<Integer> forGenBuchi(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_GENERALIZED_BUCHI, extraInfo, 1);
		int numberOfInf = extraInfoToIntegerList(ACC_GENERALIZED_BUCHI, extraInfo).get(0);

		if (numberOfInf == 0) {
			return trueConstant();
		}

		PropositionalFormula<Integer> result = null;
		for (int i = 0; i < numberOfInf; i++) {
			PropositionalFormula<Integer> inf = AtomAcceptance.Inf(i);

			if (i == 0) {
				result = inf;
			} else {
				result = Conjunction.of(result, inf);
			}
		}

		return result;
	}

	/** Get canonical for 'generalized-coBuchi' */
	public static PropositionalFormula<Integer> forGenCoBuchi(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_GENERALIZED_COBUCHI, extraInfo, 1);
		int numberOfFin = extraInfoToIntegerList(ACC_GENERALIZED_COBUCHI, extraInfo).get(0);

		if (numberOfFin == 0) {
			return falseConstant();
		}

		PropositionalFormula<Integer> result = null;
		for (int i = 0; i < numberOfFin; i++) {
			PropositionalFormula<Integer> fin = AtomAcceptance.Fin(i);

			if (i == 0) {
				result = fin;
			} else {
				result = Conjunction.of(result, fin);
			}
		}

		return result;
	}

	/** Get canonical for 'Rabin' */
	public static PropositionalFormula<Integer> forRabin(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_RABIN, extraInfo, 1);
		int numberOfPairs = extraInfoToIntegerList(ACC_RABIN, extraInfo).get(0);

		if (numberOfPairs == 0) {
			return falseConstant();
		}

		PropositionalFormula<Integer> result = null;
		for (int i = 0; i < numberOfPairs; i++) {
			PropositionalFormula<Integer> fin = AtomAcceptance.Fin(2 * i);
			PropositionalFormula<Integer> inf = AtomAcceptance.Inf(2 * i + 1);

			PropositionalFormula<Integer> pair = Conjunction.of(fin, inf);

			if (i == 0) {
				result = pair;
			} else {
				result = Disjunction.of(result, pair);
			}
		}

		return result;
	}

	/** Get canonical for 'Streett' */
	public static PropositionalFormula<Integer> forStreett(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_STREETT, extraInfo, 1);
		int numberOfPairs = extraInfoToIntegerList(ACC_STREETT, extraInfo).get(0);

		if (numberOfPairs == 0) {
			return trueConstant();
		}

		PropositionalFormula<Integer> result = null;
		for (int i = 0; i < numberOfPairs; i++) {
			PropositionalFormula<Integer> fin = AtomAcceptance.Fin(2 * i);
			PropositionalFormula<Integer> inf = AtomAcceptance.Inf(2 * i + 1);

			PropositionalFormula<Integer> pair = Disjunction.of(fin, inf);

			if (i == 0) {
				result = pair;
			} else {
				result = Conjunction.of(result, pair);
			}
		}

		return result;
	}

	/** Get canonical for 'generalized-Rabin' */
	public static PropositionalFormula<Integer> forGeneralizedRabin(List<Object> extraInfo) {
		List<Integer> parameters = extraInfoToIntegerList(ACC_GENERALIZED_RABIN, extraInfo);

		if (parameters.size() == 0) {
			throw new IllegalArgumentException("Acceptance "+ACC_GENERALIZED_RABIN+" needs at least one argument");
		}

		int numberOfPairs = parameters.get(0);
		if (parameters.size() != numberOfPairs + 1) {
			throw new IllegalArgumentException("Acceptance "+ACC_GENERALIZED_RABIN+" with " + numberOfPairs +" generalized pairs: There is not exactly one argument per pair");
		}

		PropositionalFormula<Integer> result = null;
		int currentIndex = 0;
		for (int i = 0; i < numberOfPairs; i++) {
			int numberOfInf = parameters.get(i+1);

			PropositionalFormula<Integer> fin =
        AtomAcceptance.Fin(currentIndex++);
			PropositionalFormula<Integer> pair = fin;
			for (int j = 0; j< numberOfInf; j++) {
				PropositionalFormula<Integer> inf =
          AtomAcceptance.Inf(currentIndex++);
				pair = Conjunction.of(pair, inf);
			}

			if (i == 0) {
				result = pair;
			} else {
				result = Disjunction.of(result, pair);
			}
		}

		return result;
	}

	/** Get canonical for 'parity' */
	public static PropositionalFormula<Integer> forParity(List<Object> extraInfo) {
		checkNumberOfArguments(ACC_PARITY, extraInfo, 3);

		boolean min = false;
		boolean even = false;

		String minMax = extraInfoToString(ACC_PARITY, extraInfo, 0);
    min = switch (minMax) {
      case "min" -> true;
      case "max" -> false;
      default -> throw new IllegalArgumentException(
        "For acceptance " + ACC_PARITY + ", the first argument has to be either 'min' or 'max'");
    };
		String evenOdd = extraInfoToString(ACC_PARITY, extraInfo, 1);
    even = switch (evenOdd) {
      case "even" -> true;
      case "odd" -> false;
      default -> throw new IllegalArgumentException(
        "For acceptance " + ACC_PARITY + ", the second argument has to be either 'even' or 'odd'");
    };

		int colors;
		if (!(extraInfo.get(2) instanceof Integer)) {
			throw new IllegalArgumentException("For acceptance " + ACC_PARITY + ", the third argument has to be the number of colors");
		}
		colors = (Integer)extraInfo.get(2);
		if (colors < 0) {
			throw new IllegalArgumentException("For acceptance " + ACC_PARITY + ", the third argument has to be the number of colors (non-negative)");
		}

		if (colors == 0) {
			if ( min &&  even) return trueConstant();
			if (!min &&  even) return falseConstant();
			if ( min && !even) return falseConstant();
			if (!min && !even) return trueConstant();
		}

		PropositionalFormula<Integer> result = null;

		boolean reversed = min;
		boolean infOnOdd = !even;

        for (int i = 0; i < colors; i++) {
        	int color = (reversed ? colors-i-1 : i);

        	boolean produceInf;
            if (color % 2 == 0) {
            	produceInf = !infOnOdd;
            } else {
            	produceInf = infOnOdd;
            }

            PropositionalFormula<Integer> node;
            if (produceInf) {
            	node = AtomAcceptance.Inf(color);
            } else {
            	node = AtomAcceptance.Fin(color);
            }

            if (result == null) {
            	result = node;
            } else {
            	if (produceInf) {
            		// Inf always with |
            		result = Disjunction.of(node, result);
            	} else {
            		// Fin always with &
            		result = Conjunction.of(node, result);
            	}
            }
        }

		return result;
	}

	/** Convert extra info to list of integers */
	private static List<Integer> extraInfoToIntegerList(String accName, List<Object> extraInfo) {
		List<Integer> result = new ArrayList<>(extraInfo.size());
		for (Object i : extraInfo) {
			if (!(i instanceof Integer)) {
				throw new IllegalArgumentException("For acceptance " + accName + ", all arguments have to be integers");
			}

			result.add((Integer)i);
		}

		return result;
	}

	/** Helper: Extract a String from extraInfo, throw exception otherwise */
	private static String extraInfoToString(String accName, List<Object> extraInfo, int index) {
		if (extraInfo.get(index) instanceof String)
			return (String) extraInfo.get(index);

		throw new IllegalArgumentException("Argument "+(index-1)+" for acceptance " + accName +" has to be a string!");
	}

	/** Check the number of arguments in extra info */
	private static void checkNumberOfArguments(String accName, List<Object> extraInfo, int expectedNumberOfArguments) throws IllegalArgumentException
	{
		if (expectedNumberOfArguments != extraInfo.size()) {
			throw new IllegalArgumentException("For acceptance " + accName + ", expected " + expectedNumberOfArguments + " arguments, got " + extraInfo.size());
		}
	}

}
