package org.dalquist.hubitat.logging;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

public final class AppOptions extends OptionsBase {
    @Option(name = "hub", abbrev = 'h', help = "Hub address, host name or IP", defaultValue = "hubitat-c7")
    public String hubAddr;
    
    @Option(name = "logdir", abbrev = 'd', help = "Directory to log to.", defaultValue = "/Users/edalquist/tmp/")
    public String logDir;

    @Option(name = "pattern", abbrev = 'p', help = "Log file rotation pattern.", defaultValue = "yyyyMMddHH")
    public String rotationPattern;

    @Option(name = "size", abbrev = 's', help = "Log file rotation size (MiB).", defaultValue = "100")
    public int rotationSize;
}
