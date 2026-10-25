package io.github.vnic03.profitpilot.database;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class ARFFConverter {

  public static byte[] toBytes(List<MarketIndicators> dataList) {
    if (dataList.isEmpty()) {
      throw new IllegalArgumentException("No complete indicator rows available for export.");
    }

    StringBuilder content = new StringBuilder();

    content.append("@RELATION finance\n\n");
    content.append("% SMA 20, EMA 20, RSI 14, MACD 12/26/9\n");
    content.append("@ATTRIBUTE date DATE \"yyyy-MM-dd\"\n");
    content.append("@ATTRIBUTE sma NUMERIC\n");
    content.append("@ATTRIBUTE rsi NUMERIC\n");
    content.append("@ATTRIBUTE ema NUMERIC\n");
    content.append("@ATTRIBUTE macd NUMERIC\n");
    content.append("@ATTRIBUTE signal_line NUMERIC\n\n");
    content.append("@DATA\n");

    // Decimal commas would be mistaken for ARFF field separators.
    for (MarketIndicators row : dataList) {
      content.append(
          String.format(
              Locale.US,
              "\"%s\",%.6f,%.6f,%.6f,%.6f,%.6f\n",
              row.date(),
              row.sma(),
              row.rsi(),
              row.ema(),
              row.macd(),
              row.signalLine()));
    }

    return content.toString().getBytes(StandardCharsets.UTF_8);
  }

  public static void convert(List<MarketIndicators> dataList, String outputPath) {

    byte[] content = toBytes(dataList);
    Path path = Path.of(outputPath);

    try {
      Files.write(path, content);

    } catch (IOException e) {
      throw new UncheckedIOException("Could not write ARFF file: " + path, e);
    }
  }
}
