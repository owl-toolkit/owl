package owl.automaton.symbolic;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import sun.misc.Signal;

public enum StatisticsCollector {

  STATISTICS_COLLECTOR;

  StatisticsCollector() {
    if (ENABLED) {
      Signal.handle(new Signal("INT"), sig -> System.exit(0));
      Runtime.getRuntime().addShutdownHook(new Thread(this::dumpStats));
    }
  }

  public static final boolean ENABLED = true;
  public static final boolean COMPRESSION_ENABLED = false;
  @Nullable private double[] drwdcwCompressionRates = null;
  @Nullable private double[] dswdcwCompressionRates = null;
  private double compressionRateDRW = 0;
  private double compressionRateDSW = 0;
  private double compressionRateDRWDSW = 0;
  private double compressionRateDPW = 0;
  private int numberOfGates = 0;
  @Nullable private Stage stage = null;
  private long timestamp = 0;
  private long sylvanTime = 0;
  private Map<Stage, Long> timings = new EnumMap<>(Stage.class);
  private boolean finished = false;

  public enum Stage {
    DRW_DCW,
    DRW_PRODUCT,
    DPW1,
    DSW_DCW,
    DSW_PRODUCT,
    DRW_DSW_PRODUCT,
    DPW2,
    DFI,
    SD,
    AIG
  }

  public synchronized void sylvanInitStart() {
    sylvanTime = System.currentTimeMillis();
  }

  public synchronized void sylvanInitStop() {
    sylvanTime = System.currentTimeMillis() - sylvanTime;
  }

  public synchronized void start() {
    if (ENABLED) {
      stage = Stage.DRW_DCW;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToProduct(List<SymbolicAutomaton<?>> dcws) {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DRW_DCW || stage == Stage.DSW_DCW;
      double[] compressionRates = new double[dcws.size()];
      for (int i = 0; i < dcws.size(); i++) {
        compressionRates[i] = getCompressionRateFor(dcws.get(i));
      }
      if (stage == Stage.DRW_DCW) {
        drwdcwCompressionRates = compressionRates;
        timings.put(Stage.DRW_DCW, time - timestamp - sylvanTime);
        stage = Stage.DRW_PRODUCT;
      } else {
        dswdcwCompressionRates = compressionRates;
        timings.put(Stage.DSW_DCW, time - timestamp);
        stage = Stage.DSW_PRODUCT;
      }
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToDPW1(SymbolicAutomaton<?> drw) {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DRW_PRODUCT;
      compressionRateDRW = getCompressionRateFor(drw);
      timings.put(Stage.DRW_PRODUCT, time - timestamp);
      stage = Stage.DPW1;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToDSW() {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DPW1;
      timings.put(Stage.DPW1, time - timestamp);
      stage = Stage.DSW_DCW;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToDRWDSWProduct(SymbolicAutomaton<?> dsw) {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DSW_PRODUCT;
      compressionRateDSW = getCompressionRateFor(dsw);
      timings.put(Stage.DSW_PRODUCT, time - timestamp);
      stage = Stage.DRW_DSW_PRODUCT;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToDPW2(SymbolicAutomaton<?> drwdswProduct) {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DRW_DSW_PRODUCT;
      compressionRateDRWDSW = getCompressionRateFor(drwdswProduct);
      timings.put(Stage.DRW_DSW_PRODUCT, time - timestamp);
      stage = Stage.DPW2;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToDFI(SymbolicAutomaton<?> dpw) {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DPW1 || stage == Stage.DPW2;
      compressionRateDPW = getCompressionRateFor(dpw);
      timings.put(stage, time - timestamp);
      stage = Stage.DFI;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToSD() {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DFI;
      timings.put(Stage.DFI, time - timestamp);
      stage = Stage.SD;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void advanceToAIG() {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.SD;
      timings.put(Stage.SD, time - timestamp);
      stage = Stage.AIG;
      timestamp = System.currentTimeMillis();
    }
  }

  public synchronized void stop() {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.DFI;
      timings.put(Stage.DFI, time - timestamp);
      stage = null;
      finished = true;
    }
  }

  public synchronized void stop(int numberOfGates) {
    if (ENABLED) {
      long time = System.currentTimeMillis();
      assert stage == Stage.AIG;
      this.numberOfGates = numberOfGates;
      timings.put(Stage.AIG, time - timestamp);
      stage = null;
      finished = true;
    }
  }

  public synchronized void dumpStats() {
    JsonObject stats = new JsonObject();
    stats.add("stage", stage == null ? JsonNull.INSTANCE : new JsonPrimitive(stage.toString()));
    stats.add("finished", new JsonPrimitive(finished));
    stats.add("dsw_constructed", new JsonPrimitive(timings.containsKey(Stage.DSW_PRODUCT)));
    stats.addProperty("sylvan_init_time", sylvanTime);
    if (numberOfGates > 0) {
      stats.addProperty("numberOfGates", numberOfGates);
    }
    JsonObject timings = new JsonObject();
    for (var entry : this.timings.entrySet()) {
      timings.addProperty(entry.getKey().toString(), entry.getValue());
    }
    stats.add("timings", timings);
    JsonObject compressionRates = new JsonObject();
    if (compressionRateDRW > 0) {
      compressionRates.addProperty("DRW", compressionRateDRW);
    }
    if (compressionRateDSW > 0) {
      compressionRates.addProperty("DSW", compressionRateDSW);
    }
    if (compressionRateDRWDSW > 0) {
      compressionRates.addProperty("DRWDSW", compressionRateDRWDSW);
    }
    if (compressionRateDPW > 0) {
      compressionRates.addProperty("DPW", compressionRateDPW);
    }
    JsonArray drwdcwCompressionRates = new JsonArray();
    if (this.drwdcwCompressionRates != null) {
      for (double i : this.drwdcwCompressionRates) {
        drwdcwCompressionRates.add(i);
      }
    }
    compressionRates.add("DRW_DCWs", drwdcwCompressionRates);
    JsonArray dswdcwCompressionRates = new JsonArray();
    if (this.dswdcwCompressionRates != null) {
      for (double i : this.dswdcwCompressionRates) {
        dswdcwCompressionRates.add(i);
      }
    }
    compressionRates.add("DSW_DCWs", dswdcwCompressionRates);
    stats.add("compressionRates", compressionRates);
    System.err.println(stats);
  }

  private static double getCompressionRateFor(SymbolicAutomaton<?> automaton) {
    if (COMPRESSION_ENABLED) {
      BigDecimal numberOfassignments = new BigDecimal(automaton.transitionRelation().intersection(automaton.reachableStates()).size(automaton.variableAllocation().numberOfVariables()).multiply(
        BigInteger.valueOf(automaton.variableAllocation().numberOfVariables())));
      BigDecimal numberOfNodes = BigDecimal.valueOf(automaton.transitionRelation().numberOfNodes());
      return numberOfassignments.divide(numberOfNodes, 5, RoundingMode.HALF_UP).doubleValue();
    } else {
      return 0;
    }
  }



}
