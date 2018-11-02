/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations.ltl2ldba.breakpointfree;

import owl.translations.ldba2dpa.Language;
import owl.translations.ldba2dpa.LanguageLattice;

public class BooleanLattice
  implements LanguageLattice<AcceptingComponentState, FGObligations, Void> {

  private static final Language<Void> BOTTOM = new BottomLanguage();
  private static final Language<Void> TOP = new TopLanguage();

  @Override
  public Language<Void> getBottom() {
    return BOTTOM;
  }

  @Override
  public Language<Void> getLanguage(AcceptingComponentState state) {
    return TOP;
  }

  @Override
  public Language<Void> getTop() {
    return TOP;
  }

  @Override
  public boolean isLivenessLanguage(FGObligations annotation) {
    return annotation.isPureLiveness();
  }

  @Override
  public boolean acceptsSafetyLanguage(AcceptingComponentState state) {
    return isSafetyAnnotation(state.obligations) && state.liveness == null;
  }

  @Override
  public boolean acceptsLivenessLanguage(AcceptingComponentState state) {
    return isLivenessLanguage(state.obligations) && state.safety.isTrue();
  }

  @Override
  public boolean isSafetyAnnotation(FGObligations annotation) {
    return annotation.isPureSafety();
  }

  private static class BottomLanguage implements Language<Void> {
    @Override
    public Void getT() {
      return null;
    }

    @Override
    public boolean greaterOrEqual(Language<Void> language) {
      return language instanceof BottomLanguage;
    }

    @Override
    public boolean isBottom() {
      return true;
    }

    @Override
    public boolean isTop() {
      return false;
    }

    @Override
    public Language<Void> join(Language<Void> language) {
      return language;
    }
  }

  private static class TopLanguage implements Language<Void> {
    @Override
    public Void getT() {
      return null;
    }

    @Override
    public boolean greaterOrEqual(Language<Void> language) {
      return true;
    }

    @Override
    public boolean isBottom() {
      return false;
    }

    @Override
    public boolean isTop() {
      return true;
    }

    @Override
    public Language<Void> join(Language<Void> language) {
      return this;
    }
  }
}
