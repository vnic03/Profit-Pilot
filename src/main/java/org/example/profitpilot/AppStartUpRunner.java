package org.example.profitpilot;

import org.example.profitpilot.sync.PriceImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AppStartUpRunner implements CommandLineRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(AppStartUpRunner.class);

    private final PriceImportService imports;
    private final boolean syncOnStartup;

    public AppStartUpRunner(
            PriceImportService imports,
            @Value("${profitpilot.sync.on-startup:false}")
            boolean syncOnStartup) {

        this.imports = imports;
        this.syncOnStartup = syncOnStartup;
    }

    @Override
    public void run(String... args) {
        if (!syncOnStartup) {
            logger.info("Startup price import is disabled.");
            return;
        }

        logger.info(
                "Startup import result: {}",
                imports.runConfiguredImport()
        );
    }
}
