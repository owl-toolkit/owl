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

package owl.thirdparty.jhoafparser.ast;

/**
 * Atom of a label expression (either an atomic proposition index or an alias reference)
 */
public record AtomLabel(Type type, Integer apIndex, String aliasName) {

  /** The type of the label */
	public enum Type {
		/** atomic proposition index */
		AP_INDEX,
		/** alias reference */
		ALIAS
	};

	/** Static constructor for an AP index atom */
	public static AtomLabel createAPIndex(Integer apIndex) {
		return new AtomLabel(Type.AP_INDEX, apIndex, null);
	}

	/** Static constructor for an alias reference atom */
	public static AtomLabel createAlias(String aliasName) {
		return new AtomLabel(Type.ALIAS, null, aliasName);
	}

	/** Returns true if this atom is an alias reference */
	public boolean isAlias() {
    return type == Type.ALIAS;
  }

	/**
	 * For an alias atom, return the alias name.
	 *
	 * @throws UnsupportedOperationException when invoked for an AP index atom
	 */
	public String aliasName() {
		if (!isAlias()) throw new UnsupportedOperationException(this.toString()+" is not an alias");
		return aliasName;
	}

	/**
	 * For an AP index atom, return the AP index.
	 *
	 * @throws UnsupportedOperationException when invoked for an alias atom
	 */
	public Integer apIndex() {
		if (isAlias()) throw new UnsupportedOperationException(this.toString()+" is not an AP index");
		return apIndex;
	}

	@Override
	public String toString() {
    return switch (type) {
      case AP_INDEX -> apIndex.toString();
      case ALIAS -> "@" + aliasName;
    };
	}
}
