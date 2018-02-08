package owl.run;

import static owl.run.RunUtil.checkDefaultAnnotationOption;
import static owl.run.RunUtil.checkDefaultParallelOption;
import static owl.run.RunUtil.failWithMessage;
import static owl.run.RunUtil.getDefaultAnnotationOption;
import static owl.run.RunUtil.getDefaultParallelOption;

import com.google.common.base.Strings;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleRegistry;
import owl.run.parser.OwlParser;

public final class ServerCli {
  private ServerCli() {}

  public static Options getOptions() {
    Option addressOption =
      new Option(null, "address", true, "Address to listen on (default localhost)");
    Option portOption = new Option(null, "port", true, "Port to listen on (default 5050)");
    Option allowNonLocalOption = new Option(null, "any", false, "Allow binding to any address");

    return new Options()
      .addOption(addressOption)
      .addOption(portOption)
      .addOption(allowNonLocalOption)
      .addOption(getDefaultAnnotationOption())
      .addOption(getDefaultParallelOption());
  }

  @SuppressWarnings("PMD.SystemPrintln")
  public static Callable<Void> build(CommandLine settings, Pipeline pipeline) {
    @Nullable
    InetAddress address;
    if (Strings.isNullOrEmpty(settings.getOptionValue("address"))) {
      try {
        address = InetAddress.getLocalHost();
      } catch (UnknownHostException e) {
        throw failWithMessage("Could not resolve localhost: " + e.getMessage(), e);
      }
    } else {
      try {
        address = Inet4Address.getByName(settings.getOptionValue("address"));
      } catch (UnknownHostException e) {
        throw failWithMessage("Failed to resolve given address: " + e.getMessage(), e);
      }
      // TODO Is this entirely correct?
      if (!(address.isLinkLocalAddress() || address.isLoopbackAddress())) {
        if (settings.hasOption("any")) {
          System.err.println("WARNING: Your server may be accessible by others (binding to "
            + address + "). Make sure that this is what you want!");
        } else {
          throw failWithMessage("Refusing to bind on non-local address " + address
            + " (neither link-local nor loop-back)", null);
        }
      }
    }

    int port;
    if (Strings.isNullOrEmpty(settings.getOptionValue("port"))) {
      port = 5050;
    } else {
      try {
        port = Integer.parseInt(settings.getOptionValue("port"));
      } catch (NumberFormatException e) {
        throw failWithMessage("Invalid value for port", e);
      }
    }

    boolean annotations = checkDefaultAnnotationOption(settings);
    boolean parallel = checkDefaultParallelOption(settings);

    return new ServerRunner(pipeline, () -> DefaultEnvironment.of(annotations, false, parallel),
      address, port);
  }

  public static void main(String... args) {
    OwlParser parseResult =
      OwlParser.parse(args, new DefaultParser(), getOptions(), OwlModuleRegistry.DEFAULT_REGISTRY);
    if (parseResult == null) {
      System.exit(1);
      return;
    }

    RunUtil.execute(build(parseResult.globalSettings, parseResult.pipeline));
  }

}
