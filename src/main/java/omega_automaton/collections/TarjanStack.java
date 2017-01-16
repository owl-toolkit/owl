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

package omega_automaton.collections;


import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * it is designed to be a stack that can handle push, pop, peek, contains and
 * isEmpty in constant time under the assumption, that the hash function of the
 * elements is distributed reasonably well. However, as the TrivialTarjanStack,
 * some subclasses may take longer in theory, but they might be faster, because
 * we are using only rather small automata.
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!Important!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * This class always assumes the Objects to be unique, i.e. no Object can be in
 * the stack twice
 */
public class TarjanStack<E> extends AbstractCollection<E> implements Deque<E> {

  Set<E> elementsInTheStack;
  Deque<E> stack;

  public TarjanStack() {
    stack = new ArrayDeque<>();
    elementsInTheStack = new HashSet<>();
  }

  @Override
  public boolean add(E e) {
    elementsInTheStack.add(e);
    return stack.add(e);
  }

  @Override
  public void addFirst(E e) {
    stack.addFirst(e);
    elementsInTheStack.add(e);
  }

  @Override
  public void addLast(E e) {
    stack.addLast(e);
    elementsInTheStack.add(e);
  }

  @Override
  public void clear() {
    elementsInTheStack.clear();
    stack.clear();
  }

  @Override
  public boolean contains(Object o) {
    return elementsInTheStack.contains(o);
  }

  @Nonnull
  @Override
  public Iterator<E> descendingIterator() {
    return stack.descendingIterator();
  }

  @Override
  public E element() {
    return stack.element();
  }

  @Override
  public E getFirst() {
    return stack.getFirst();
  }

  @Override
  public E getLast() {
    return stack.getLast();
  }

  @Override
  public boolean isEmpty() {
    return elementsInTheStack.isEmpty();
  }

  @Nonnull
  @Override
  public Iterator<E> iterator() {
    return stack.iterator();
  }

  @Override
  public boolean offer(E e) {
    elementsInTheStack.add(e);
    return stack.offer(e);
  }

  @Override
  public boolean offerFirst(E e) {
    elementsInTheStack.add(e);
    return stack.offerFirst(e);
  }

  @Override
  public boolean offerLast(E e) {
    elementsInTheStack.add(e);
    return stack.offerLast(e);
  }

  @Override
  public E peek() {
    return stack.peek();
  }

  @Override
  public E peekFirst() {
    return stack.peekFirst();
  }

  @Override
  public E peekLast() {
    return stack.peekLast();
  }

  @Override
  public E poll() {
    E e = stack.poll();
    elementsInTheStack.remove(e);
    return e;
  }

  @Override
  public E pollFirst() {
    E e = stack.pollFirst();
    elementsInTheStack.remove(e);
    return e;
  }

  @Override
  public E pollLast() {
    E e = stack.pollLast();
    elementsInTheStack.remove(e);
    return e;
  }

  @Override
  public E pop() {
    E e = stack.pop();
    elementsInTheStack.remove(e);
    return e;
  }

  @Override
  public void push(E e) {
    stack.push(e);
    elementsInTheStack.add(e);
  }

  @Override
  public E remove() {
    E e = stack.remove();
    elementsInTheStack.remove(e);
    return e;
  }

  @Override
  public boolean remove(Object o) {
    elementsInTheStack.remove(o);
    return stack.remove(o);
  }

  @Override
  public E removeFirst() {
    E e = stack.removeFirst();
    elementsInTheStack.remove(e);
    return e;
  }

  @Override
  public boolean removeFirstOccurrence(Object o) {
    if (this.contains(o)) {
      this.remove(o);
      return true;
    }

    return false;
  }

  @Override
  public E removeLast() {
    E e = stack.removeLast();
    elementsInTheStack.remove(e);
    return e;
  }

  @Override
  public boolean removeLastOccurrence(Object o) {
    return removeFirstOccurrence(o);
  }

  @Override
  public int size() {
    return stack.size();
  }

  @Nonnull
  @Override
  public Object[] toArray() {
    return stack.toArray();
  }

  @Nonnull
  @Override
  public <T> T[] toArray(@Nonnull T[] a) {
    return stack.toArray(a);
  }
}
