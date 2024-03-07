package org.example.profitpilot.visuals;

import org.example.profitpilot.database.DatabaseService;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.util.Map;

public class Prototype {

    private final DatabaseService dbService;

    public Prototype(DatabaseService dbService) {
        this.dbService = dbService;
    }

    public void createChart(String symbol) {
        Map<String, Double> prices = dbService.getClosePrices(symbol);
        Map<String, Double> smaData = dbService.getSMA(symbol);
        Map<String, Double> emaData = dbService.getEMA(symbol);
        Map<String, Double> macdData = dbService.getMACD(symbol);

        TimeSeries priceSeries = createTimeSeries("Close Prices", prices);
        TimeSeries smaSeries = createTimeSeries("SMA", smaData);
        TimeSeries emaSeries = createTimeSeries("EMA", emaData);
        TimeSeries macdSeries =createTimeSeries("MACD", macdData);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(priceSeries);
        dataset.addSeries(smaSeries);
        dataset.addSeries(emaSeries);
        dataset.addSeries(macdSeries);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Stock Prices for " + symbol,
                "Date",
                "Price",
                dataset,
                false,
                true,
                false);

            displayChart(chart);
    }

    private static TimeSeries createTimeSeries(String title, Map<String, Double> data) {
        TimeSeries series = new TimeSeries(title);
        data.forEach((date, value) -> {
            String[] parts = date.split("-");

            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int dayOfMonth = Integer.parseInt(parts[2]);

            series.add(new Day(dayOfMonth, month, year), value);
        });
        return series;
    }

    private void displayChart(JFreeChart chart) {
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(1300, 800));
        JFrame frame = new JFrame();
        frame.setContentPane(chartPanel);
        frame.pack();
        frame.setTitle("Financial Data Visualization");
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
