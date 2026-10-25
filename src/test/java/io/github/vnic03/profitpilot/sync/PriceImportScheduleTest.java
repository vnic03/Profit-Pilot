package io.github.vnic03.profitpilot.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.support.CronExpression;

class PriceImportScheduleTest {

  @ParameterizedTest
  @CsvSource({
    "2026-10-13T06:11:00-04:00[America/New_York], " + "2026-10-13T08:10:00-04:00[America/New_York]",
    "2026-10-16T18:11:00-04:00[America/New_York], " + "2026-10-17T06:10:00-04:00[America/New_York]",
    "2026-03-07T18:11:00-05:00[America/New_York], " + "2026-03-08T06:10:00-04:00[America/New_York]",
    "2026-10-31T18:11:00-04:00[America/New_York], " + "2026-11-01T06:10:00-05:00[America/New_York]"
  })
  void configuredScheduleUsesNewYorkTime(String previousTime, String expectedNextTime)
      throws Exception {

    Properties properties = new Properties();

    try (InputStream input = getClass().getResourceAsStream("/application-auto.properties")) {

      assertNotNull(input);
      properties.load(input);
    }

    assertEquals("America/New_York", properties.getProperty("profitpilot.sync.zone"));

    CronExpression schedule = CronExpression.parse(properties.getProperty("profitpilot.sync.cron"));

    assertEquals(
        ZonedDateTime.parse(expectedNextTime), schedule.next(ZonedDateTime.parse(previousTime)));
  }
}
