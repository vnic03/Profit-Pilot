package org.example.profitpilot.database;

import org.example.profitpilot.api.alpha_vantage.AlphaVantageCache;
import org.example.profitpilot.api.alpha_vantage.StockChange;
import org.example.profitpilot.feature_engineering.MarketIndicatorsCalculator;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;

import java.sql.*;
import java.sql.Date;
import java.util.*;

@Service
public class DatabaseService {

    private final DataSource dataSource;
    private final AlphaVantageCache alphaVantageCache;

    public DatabaseService(DataSource dataSource, AlphaVantageCache alphaVantageCache) {
        this.dataSource = dataSource;
        this.alphaVantageCache = alphaVantageCache;
    }

    public Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    public void insertSharePrice(List<SharePrice> sharePrices) {
        final String SQL =
                "INSERT INTO aktienkurse(symbol, date, open, high, low, close, volume) VALUES(?,?,?,?,?,?,?)";

        try (Connection connection = connect();
            PreparedStatement pstmt = connection.prepareStatement(SQL))
        {
            int count = 0;

            for (SharePrice sp : sharePrices) {
                if (!validateData(
                        sp.symbol(), sp.date(), sp.open(), sp.high(), sp.low(), sp.close(), sp.volume()))
                {
                    continue;
                }
                pstmt.setString(1, sp.symbol());
                pstmt.setDate(2, java.sql.Date.valueOf(sp.date()));
                pstmt.setDouble(3, sp.open());
                pstmt.setDouble(4, sp.high());
                pstmt.setDouble(5, sp.low());
                pstmt.setDouble(6, sp.close());
                pstmt.setLong(7,sp.volume());

                pstmt.addBatch();

                if (++count % 100 == 0 || count == sharePrices.size()) {
                    pstmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void insertFinancialIndicator
            (String symbol, String date, Double sma, Double rsi, Double ema, Double macd, Double signalLine)
    {
        final String SQL =
                "INSERT INTO finanzindikatoren " +
                        "(symbol, datum, sma, rsi, ema, macd, signal_line) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = connect();
             PreparedStatement pstmt = connection.prepareStatement(SQL)) {

            pstmt.setString(1, symbol);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setDouble(3, sma);
            pstmt.setDouble(4, rsi);
            pstmt.setDouble(5, ema);
            pstmt.setDouble(6, macd);
            pstmt.setDouble(7, signalLine);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

   public Map<String, Double> getHigh(String symbol) {
        return getMarketData(symbol, "high");
   }

    public Map<String, Double> getLow(String symbol) {
        return getMarketData(symbol, "low");
    }

    public Map<String, Double> getOpen(String symbol) {
        return getMarketData(symbol, "open");
    }

    public Map<String, Double> getClosePrices(String symbol) {
        return getMarketData(symbol, "close");
    }

    public Map<String, Long> getVolume(String symbol) {
        Map<String, Long> data = new HashMap<>();
        String SQL = "SELECT date, volume FROM aktienkurse WHERE symbol = ? ORDER BY date ASC";

        try (Connection connection = connect();
             PreparedStatement pstmt = connection.prepareStatement(SQL)) {
            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getString("date");
                    long volume = rs.getLong("volume");
                    data.put(date, volume);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching volume data: " + e.getMessage());
        }
        return data;
    }

   private Map<String, Double> getMarketData(String symbol, String column) {
       Map<String, Double> data = new HashMap<>();
       final String SQL = "SELECT date, " + column + " FROM aktienkurse WHERE symbol = ? ORDER BY date ASC";

       try (Connection connection = connect();
            PreparedStatement pstmt = connection.prepareStatement(SQL)) {
           pstmt.setString(1, symbol);
           try (ResultSet rs = pstmt.executeQuery()) {
               while (rs.next()) {
                   String date = rs.getString("date");
                   double value = rs.getDouble(column);
                   data.put(date, value);
               }
           }
       } catch (SQLException e) {
           System.out.println("Error fetching " + column + " data: " + e.getMessage());
       }
       return data;
   }

   private Map<String, Double> getClosingPrices(String symbol) {
       Map<String, Double> prices = new LinkedHashMap<>();
       final String SQL = "SELECT date, close FROM aktienkurse WHERE symbol = ? ORDER BY date ASC";

       try (Connection connection = connect();
            PreparedStatement pstmt = connection.prepareStatement(SQL))
       {
           pstmt.setString(1, symbol);
           try (ResultSet rs = pstmt.executeQuery()) {
               while (rs.next()) {
                   String date = rs.getString("date");
                   double close = rs.getDouble("close");
                   prices.put(date, close);
               }
           }
       } catch (SQLException e) {
           System.out.println("Error fetching closing prices: " + e.getMessage());
       }
       return prices;
   }

   public Map<String, Double> getSMA(String symbol, int period) {
        Map<String, Double> prices = getClosingPrices(symbol);
        return MarketIndicatorsCalculator.SMA(prices, period);
   }

    public Map<String, Double> getRSI(String symbol, int period) {
        Map<String, Double> prices = getClosingPrices(symbol);
        return MarketIndicatorsCalculator.RSI(prices, period);
    }

    public Map<String, Double> getEMA(String symbol, int period) {
        Map<String, Double> prices = getClosingPrices(symbol);
        return MarketIndicatorsCalculator.EMA(prices, period);
    }

    public Map<String, Double> getMACD(String symbol) {
        return getFinancialIndicator(symbol, "macd");
    }

    private Map<String, Double> getFinancialIndicator(String symbol, String indicator) {
        final String SQL = "SELECT datum, " + indicator + " FROM finanzindikatoren WHERE symbol = ? ORDER BY datum ASC";
        Map<String, Double> data = new HashMap<>();

        try (Connection connection = connect();
             PreparedStatement pstmt = connection.prepareStatement(SQL)) {

            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getString("datum");
                    double value = rs.getDouble(indicator);
                    data.put(date, value);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching " + indicator + " data: " + e.getMessage());
        }
        return data;
    }

    public List<MarketIndicators> queryFinancialDataForSymbol(String symbol) {
        List<MarketIndicators> financialDataList = new ArrayList<>();
        final String SQL =
                "SELECT datum, sma, rsi, ema, macd, signal_line FROM finanzindikatoren WHERE symbol = ? ORDER BY datum ASC";

        try (Connection connection = connect();
            PreparedStatement pstmt = connection.prepareStatement(SQL))
        {
            pstmt.setString(1, symbol);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String date = rs.getString("datum");
                double sma = rs.getDouble("sma");
                double rsi = rs.getDouble("rsi");
                double ema = rs.getDouble("ema");
                double macd = rs.getDouble("macd");
                double signalLine = rs.getDouble("signal_line");

                MarketIndicators financialData = new MarketIndicators(date, sma, rsi, ema, macd, signalLine);
                financialDataList.add(financialData);
            }
        } catch (SQLException e) {
            System.out.println("Error querying financial data: " + e.getMessage());
        }
        return financialDataList;
    }

    private boolean validateData
            (String symbol,String date, double open, double high,
             double low, double close, long volume)
    {
        if (symbol == null || symbol.isEmpty()) return false;
        if (open < 0 || high < 0 || low < 0 || close < 0) return false;
        return !(high < low);
    }

    public Map<String, List<StockChange>> getWinnersAndLosers(int amount) {
        return alphaVantageCache.winnersAndLooser(amount);
    }

    public record SharePrice
            (String symbol, String date, Double open, Double high, Double low, Double close, Long volume) { }
}
