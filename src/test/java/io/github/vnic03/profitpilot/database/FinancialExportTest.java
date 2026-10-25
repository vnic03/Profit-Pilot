package io.github.vnic03.profitpilot.database;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import weka.core.Instances;

class FinancialExportTest {

  private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);

  @TempDir Path temporaryDirectory;

  private EmbeddedDatabase dataSource;
  private JdbcTemplate jdbc;
  private DatabaseService database;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .build();

    jdbc = new JdbcTemplate(dataSource);

    jdbc.execute(
        """
                CREATE TABLE aktienkurse (
                    symbol VARCHAR(10) NOT NULL,
                    date DATE NOT NULL,
                    close DOUBLE PRECISION NOT NULL,
                    PRIMARY KEY (symbol, date)
                )
                """);

    database = new DatabaseService(dataSource);
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null) {
      dataSource.shutdown();
    }
  }

  @Test
  void unknownSymbolReturnsNoExportRows() {
    assertTrue(database.queryFinancialDataForSymbol("UNKNOWN").isEmpty());
  }

  @Test
  void fewerThan34PricesProduceNoCompleteRows() {
    insertPrices("IBM", 33, 100.0, 1.0);

    assertTrue(database.queryFinancialDataForSymbol("IBM").isEmpty());
  }

  @Test
  void exactly34PricesProduceFirstCompleteRow() {
    insertPrices("IBM", 34, 100.0, 1.0);

    List<MarketIndicators> rows = database.queryFinancialDataForSymbol("IBM");

    assertEquals(1, rows.size());

    MarketIndicators row = rows.get(0);

    assertEquals(START_DATE.plusDays(33).toString(), row.date());
    assertEquals(123.5, row.sma(), 0.000001);
    assertEquals(123.5, row.ema(), 0.000001);
    assertEquals(100.0, row.rsi(), 0.000001);
    assertEquals(7.0, row.macd(), 0.000001);
    assertEquals(7.0, row.signalLine(), 0.000001);
  }

  @Test
  void hundredPricesProduce67SortedRowsForRequestedSymbol() {
    insertPrices("IBM", 100, 100.0, 1.0);
    insertPrices("AAPL", 100, 1000.0, 0.0);

    List<MarketIndicators> rows = database.queryFinancialDataForSymbol("IBM");

    assertEquals(67, rows.size());

    for (int index = 0; index < rows.size(); index++) {
      int priceIndex = index + 33;
      MarketIndicators row = rows.get(index);

      assertEquals(START_DATE.plusDays(priceIndex).toString(), row.date());

      assertEquals(100.0 + priceIndex - 9.5, row.sma(), 0.000001);

      assertEquals(100.0 + priceIndex - 9.5, row.ema(), 0.000001);

      assertEquals(100.0, row.rsi(), 0.000001);
      assertEquals(7.0, row.macd(), 0.000001);
      assertEquals(7.0, row.signalLine(), 0.000001);
    }
  }

  @Test
  void exportedFileCanBeReadByWeka() throws Exception {
    insertPrices("IBM", 100, 100.0, 1.0);

    Path output = temporaryDirectory.resolve("ibm.arff");

    ARFFConverter.convert(database.queryFinancialDataForSymbol("IBM"), output.toString());

    try (var reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {

      Instances data = new Instances(reader);

      assertEquals("finance", data.relationName());
      assertEquals(67, data.numInstances());
      assertEquals(6, data.numAttributes());

      List<String> expectedAttributes = List.of("date", "sma", "rsi", "ema", "macd", "signal_line");

      for (int index = 0; index < expectedAttributes.size(); index++) {
        assertEquals(expectedAttributes.get(index), data.attribute(index).name());
      }

      assertTrue(data.attribute(0).isDate());

      for (int index = 1; index < data.numAttributes(); index++) {
        assertTrue(data.attribute(index).isNumeric());
      }

      assertEquals(
          data.attribute(0).parseDate(START_DATE.plusDays(33).toString()),
          data.instance(0).value(0),
          0.001);

      assertEquals(123.5, data.instance(0).value(1), 0.000001);
      assertEquals(100.0, data.instance(0).value(2), 0.000001);
      assertEquals(123.5, data.instance(0).value(3), 0.000001);
      assertEquals(7.0, data.instance(0).value(4), 0.000001);
      assertEquals(7.0, data.instance(0).value(5), 0.000001);

      for (int row = 0; row < data.numInstances(); row++) {
        for (int column = 0; column < data.numAttributes(); column++) {
          assertFalse(data.instance(row).isMissing(column));
          assertTrue(Double.isFinite(data.instance(row).value(column)));
        }
      }
    }
  }

  @Test
  void emptyExportPreservesExistingFile() throws Exception {
    Path output = temporaryDirectory.resolve("existing.arff");
    String original = "Existing export";

    Files.writeString(output, original, StandardCharsets.UTF_8);

    assertThrows(
        IllegalArgumentException.class, () -> ARFFConverter.convert(List.of(), output.toString()));

    assertEquals(original, Files.readString(output, StandardCharsets.UTF_8));
  }

  @Test
  void writeFailureIsReportedToCaller() {
    List<MarketIndicators> rows =
        List.of(new MarketIndicators("2026-10-05", 100.0, 50.0, 100.0, 0.0, 0.0));

    // A directory cannot be opened as an output file.
    assertThrows(
        UncheckedIOException.class,
        () -> ARFFConverter.convert(rows, temporaryDirectory.toString()));
  }

  private void insertPrices(String symbol, int count, double firstPrice, double dailyIncrease) {

    // Insert backwards to check that the export sorts by date.
    for (int index = count - 1; index >= 0; index--) {
      jdbc.update(
          """
                    INSERT INTO aktienkurse (symbol, date, close)
                    VALUES (?, ?, ?)
                    """,
          symbol,
          java.sql.Date.valueOf(START_DATE.plusDays(index)),
          firstPrice + index * dailyIncrease);
    }
  }
}
