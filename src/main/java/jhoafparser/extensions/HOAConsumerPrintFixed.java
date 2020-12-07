/*
 * This file is from jhoafparser.
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

package jhoafparser.extensions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.regex.Pattern;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;

/**
 * This {@code HOAConsumer} renders the method calls
 * to produce a valid HOA automaton output.
 */
@SuppressWarnings("PMD")
public class HOAConsumerPrintFixed implements HOAConsumer {
  private static final Pattern PATTERN_1 = Pattern.compile("\\\\");
  private static final Pattern PATTERN_2 = Pattern.compile("\"");

  /** The output writer */
	private BufferedWriter out;

	/**
	 * Constructor
	 * @param out the {@code OutputStream}
	 */
	public HOAConsumerPrintFixed(Writer out) {
		this.out = out instanceof BufferedWriter ? (BufferedWriter) out : new BufferedWriter(out);
	}

	@Override
	public boolean parserResolvesAliases() {
		return false;
	}

	@Override
	public void notifyHeaderStart(String version)
    throws HOAConsumerException {

	  try {
			out.write("HOA: " + version);
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void setNumberOfStates(int numberOfStates)
    throws HOAConsumerException {

	  try {
			out.write("States: " + numberOfStates);
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void addStartStates(List<Integer> stateConjunction)
    throws HOAConsumerException {

	  try {
			out.write("Start: ");
			boolean first = true;
			for (Integer state : stateConjunction) {
				if (!first) out.write(" & ");
				first = false;
				out.write(state.toString());
			}
      out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void addAlias(String name, BooleanExpression<AtomLabel> labelExpr)
    throws HOAConsumerException {

	  try {
			out.write(String.format("Alias: @%s %s", name, labelExpr));
      out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void setAPs(List<String> aps)
    throws HOAConsumerException {

	  try {
			out.write("AP: ");
			out.write(Integer.toString(aps.size()));
			for (String ap : aps) {
				out.write(" ");
				out.write(quoteString(ap));
			}
      out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void setAcceptanceCondition(int numberOfSets, BooleanExpression<AtomAcceptance> accExpr)
    throws HOAConsumerException {

	  try {
			out.write("Acceptance: ");
			out.write(Integer.toString(numberOfSets));
			out.write(" ");
			out.write(accExpr.toString());
      out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void provideAcceptanceName(String name, List<Object> extraInfo)
    throws HOAConsumerException {

	  try {
			out.write("acc-name: ");
			out.write(name);
			for (Object info : extraInfo) {
				out.write(" ");
				out.write(info.toString());
			}
      out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void setName(String name)
    throws HOAConsumerException {

	  try {
			out.write("name: ");
			out.write(quoteString(name));
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void setTool(String name, String version)
    throws HOAConsumerException {

	  try {
			out.write("tool: ");
			out.write(quoteString(name));
			if (version != null) {
			    out.write(' ' + quoteString(version));
			}
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void addProperties(List<String> properties)
    throws HOAConsumerException {

	  try {
			out.write("properties: ");
			for (String property : properties) {
				out.write(property);
				out.write(" ");
			}
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void addMiscHeader(String name, List<Object> content)
    throws HOAConsumerException {

	  try {
			out.write(name+": ");
			for (Object c : content) {
				out.write(c.toString());
				out.write(" ");
			}
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void notifyBodyStart()
    throws HOAConsumerException {

	  try {
			out.write("--BODY--");
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void addState(int id, String info, BooleanExpression<AtomLabel> labelExpr,
    List<Integer> accSignature)
    throws HOAConsumerException {

	  try {
			out.write("State: ");

			if (labelExpr != null) {
				out.write(String.format("[%s] ", labelExpr));
			}

			out.write(Integer.toString(id));
			if (info != null) {
				out.write(" ");
				out.write(quoteString(info));
			}

			if (accSignature != null) {
				out.write(" {");
				boolean first = true;
				for (Integer acc : accSignature) {
					if (!first) out.write(" ");
					first = false;
					out.write(acc.toString());
				}
				out.write("}");
			}

			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void addEdgeImplicit(int stateId, List<Integer> conjSuccessors,
    List<Integer> accSignature)
    throws HOAConsumerException {

	  try {
			boolean first = true;
			for (Integer succ : conjSuccessors) {
				if (!first) out.write("&");
				first = false;
				out.write(succ.toString());
			}
			if (accSignature != null) {
				out.write(" {");
				first = true;
				for (Integer acc : accSignature) {
					if (!first) out.write(" ");
					first = false;
					out.write(acc.toString());
				}
				out.write("}");
			}
			out.newLine();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void addEdgeWithLabel(int stateId, BooleanExpression<AtomLabel> labelExpr,
		List<Integer> conjSuccessors, List<Integer> accSignature)
    throws HOAConsumerException {

	  try {
			if (labelExpr != null) {
				out.write("[");
				out.write(labelExpr.toString());
				out.write("] ");
			}

			boolean first = true;
			for (Integer succ : conjSuccessors) {
				if (!first) out.write("&");
				first = false;
				out.write(succ.toString());
			}

			if (accSignature != null) {
				out.write(" {");
				first = true;
				for (Integer acc : accSignature) {
					if (!first) out.write(" ");
					first = false;
					out.write(acc.toString());
				}
				out.write("}");
			}
			out.newLine();
		} catch (IOException e) {
		  throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void notifyEndOfState(int stateId) throws HOAConsumerException {}

	@Override
	public void notifyEnd() throws HOAConsumerException {
		try {
			out.write("--END--");
			out.newLine();
			out.flush();
		} catch (IOException e) {
      throw new HOAConsumerException(e.toString());
    }
	}

	@Override
	public void notifyAbort() {
		try {
			out.write("--ABORT--");
			out.newLine();
			out.flush();
		} catch (IOException e) {
      throw new RuntimeException(e);
    }
	}

	/** Returns the argument, quoted according to HOA quoting rules.*/
	protected static String quoteString(String s) {
		return String.format("\"%s\"",
      PATTERN_2.matcher(
        PATTERN_1.matcher(s)
          .replaceAll("\\\\"))
        .replaceAll("\\\""));
	}

	@Override
	public void notifyWarning(String warning) throws HOAConsumerException {
		throw new HOAConsumerException(warning);
	}

}
