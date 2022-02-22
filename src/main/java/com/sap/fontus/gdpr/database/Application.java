package com.sap.fontus.gdpr.database;

import picocli.CommandLine;


import java.util.concurrent.Callable;

@CommandLine.Command(
        description = "Performs GDPR tainting related operations on a database",
        name = "GDPR Tainting Helper",
        mixinStandardHelpOptions = true,
        version = "0.0.1"
)
public class Application  implements Callable<Void> {

    @CommandLine.Option(
            names = {"-m", "--mode"},
            required = true,
            paramLabel = "Mode of Operation",
            description = "Mode of operation. Valid values:  ${COMPLETION-CANDIDATES}",
            defaultValue = "expiry"
    )
    private Mode mode;

    public static void main(String[] args) {
        new CommandLine(new Application())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
    }

    @Override
    public Void call() throws Exception {
        Processor processor = new Processor();
        switch(this.mode) {
            case EXPIRY:
                processor.run();
                break;
            case STATISTICS:
                processor.run();
                break;
            case SUBJECT_ACCESS_REQUEST:
                processor.run();
                break;
            default:
                System.out.printf("Mode %s is invalid!%n", this.mode);
        }
        return null;
    }
}
