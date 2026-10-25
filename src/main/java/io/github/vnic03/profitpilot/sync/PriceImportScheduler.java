package io.github.vnic03.profitpilot.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class PriceImportScheduler {

  private static final Logger logger = LoggerFactory.getLogger(PriceImportScheduler.class);

  private final PriceImportService imports;

  public PriceImportScheduler(PriceImportService imports) {
    this.imports = imports;
  }

  @Scheduled(
      cron = "${profitpilot.sync.cron:-}",
      zone = "${profitpilot.sync.zone:America/New_York}")
  public void refreshPrices() {
    try {
      logger.info("Scheduled import result: {}", imports.runConfiguredImport());
    } catch (RuntimeException e) {
      logger.error("Scheduled import could not run.", e);
    }
  }
}
