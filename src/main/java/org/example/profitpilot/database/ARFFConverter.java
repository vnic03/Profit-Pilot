package org.example.profitpilot.database;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ARFFConverter {

    public static void convert(List<MarketIndicators> dataList, String outputPath)
    {

        try (FileWriter writer = new FileWriter(outputPath)) {

            writer.write("@RELATION finance\n\n");
            writer.write("@ATTRIBUTE date DATE \"yyyy-MM-dd\"\n");
            writer.write("@ATTRIBUTE sma NUMERIC\n");
            writer.write("@ATTRIBUTE rsi NUMERIC\n");
            writer.write("@ATTRIBUTE ema NUMERIC\n\n");
            writer.write("@ATTRIBUTE macd NUMERIC\n");
            writer.write("@ATTRIBUTE signal_line NUMERIC\n\n");
            writer.write("@DATA\n");

            for(MarketIndicators data : dataList) {
                writer.write(String.format(Locale.US, "%s, %.6f, %.6f, %.6f, %.6f, %.6f\n",
                        data.date(), data.sma(), data.rsi(), data.ema(), data.macd(), data.signalLine()));
            }
        } catch (IOException e) {
            System.out.println("Error writing ARFF file: " + e.getMessage());
        }
    }
}
