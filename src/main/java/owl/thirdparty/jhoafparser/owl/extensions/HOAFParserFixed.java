/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.thirdparty.jhoafparser.owl.extensions;

import java.io.Reader;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerFactory;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

/**
 * Public interface to the HOA format parser.
 * <p>
 * Note that the parser is non-reentrant, i.e., it is
 * not possible to parse two streams at the same time!
 */
@SuppressWarnings("PMD")
public class HOAFParserFixed
{
	/**
	 * Entry point for parsing a stream of automata in HOA format.
	 * <br> Note: this parser is non-reentrant, i.e., it is
	 * not possible to parse two streams at the same time!
	 *
	 * @param str The input stream with the automaton description
	 * @param userFactory A factory that produces HOAConsumers, one for each automaton encountered,
	 *                      that receive the notifications about the parsed elements from the parser
	 */
	public static void parseHOA(Reader str, HOAConsumerFactory userFactory) throws ParseException {
		HOAFParserCCFixed.parseHOA(str, userFactory, null);
	}
}
