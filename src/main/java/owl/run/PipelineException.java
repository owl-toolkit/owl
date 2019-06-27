/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.run;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.concurrent.ExecutionException;

public class PipelineException extends RuntimeException implements Serializable {
  private static final long serialVersionUID = -8083892132914818185L;

  public PipelineException(String message, Throwable cause) {
    super(message, cause);
  }

  public static PipelineException propagate(ExecutionException e) {
    checkNotNull(e);
    Throwable ex = e;
    while (ex instanceof ExecutionException) {
      Throwable cause = ex.getCause();
      if (cause == null) {
        throw new PipelineException(ex.getMessage(), ex);
      }
      ex = cause;
    }
    throw new PipelineException(ex.getMessage(), ex);
  }
}
