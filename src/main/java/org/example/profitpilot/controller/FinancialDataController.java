package org.example.profitpilot.controller;

import org.example.profitpilot.api.StockChange;
import org.example.profitpilot.database.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
public class FinancialDataController {

    private final DatabaseService dbService;

    @Autowired
    public FinancialDataController(DatabaseService dbService) {
        this.dbService = dbService;
    }

    @GetMapping("/high/{symbol}")
    public ResponseEntity<Map<String, Double>> getHigh(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getHigh(symbol));
    }

    @GetMapping("/low/{symbol}")
    public ResponseEntity<Map<String, Double>> getLow(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getLow(symbol));
    }

    @GetMapping("/open/{symbol}")
    public ResponseEntity<Map<String, Double>> getOpen(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getOpen(symbol));
    }

    @GetMapping("/volume/{symbol}")
    public ResponseEntity<Map<String, Long>> getVolume(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getVolume(symbol));
    }

    @GetMapping("/prices/{symbol}")
    public ResponseEntity<Map<String, Double>> getClosePrices(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getClosePrices(symbol));
    }

    @GetMapping("/macd/{symbol}")
    public ResponseEntity<Map<String, Double>> getMACD(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getMACD(symbol));
    }

    @GetMapping("/dynamicSMA/{symbol}/{period}")
    public ResponseEntity<Map<String, Double>> getSMA(@PathVariable String symbol, @PathVariable int period) {
        return ResponseEntity.ok(dbService.getSMA(symbol, period));
    }

    @GetMapping("/dynamicEMA/{symbol}/{period}")
    public ResponseEntity<Map<String, Double>> getEMA(@PathVariable String symbol, @PathVariable int period) {
        return ResponseEntity.ok(dbService.getEMA(symbol, period));
    }

    @GetMapping("/dynamicRSI/{symbol}/{period}")
    public ResponseEntity<Map<String, Double>> getRSI(@PathVariable String symbol, @PathVariable int period) {
        return ResponseEntity.ok(dbService.getRSI(symbol, period));
    }

    @GetMapping("/winnersAndLosers/{symbol}/{amount}")
    public ResponseEntity<Map<String, List<StockChange>>> getWinnersAndLosers
            (@PathVariable String symbol, @PathVariable int amount)
    {
        Map<String, List<StockChange>> wandL = dbService.calculateWinnersAndLosers(symbol, amount);
        if (wandL.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(wandL);
    }
}
