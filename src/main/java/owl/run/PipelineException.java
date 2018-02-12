package owl.run;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ExecutionException;

public class PipelineException extends RuntimeException {
  public PipelineException(String message) {
    super(message);
  }

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
