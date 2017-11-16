package owl.run;

public final class PipelineExecutionException extends RuntimeException {
  private PipelineExecutionException(Throwable cause) {
    super(cause);
  }

  public static Throwable unwrap(Throwable exception) {
    if (exception instanceof PipelineExecutionException) {
      return exception.getCause();
    }

    return exception;
  }

  public static PipelineExecutionException wrap(Throwable cause) {
    if (cause instanceof PipelineExecutionException) {
      return (PipelineExecutionException) cause;
    }

    return new PipelineExecutionException(cause);
  }
}
