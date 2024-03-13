import React, {useEffect, useState} from 'react';
import { BrowserRouter as Router } from 'react-router-dom';
import './App.css';
import Navbar from "./components/navbar/Navbar";
import MarketMovers, { WinnersAndLosers} from "./components/market_movers/MarketMovers";
import FinanceData, {FinanceDataStructure, symbols} from "./service/FinanceData";
import {getWinnersAndLosers} from "./service/financeService";
import News from "./components/news/News";


function App() {
    const [symbol, setSymbol] = useState(symbols[0]);
    const [financeData, setFinanceData] = useState<FinanceDataStructure>({});

    const [movers, setMovers] = useState<WinnersAndLosers>({ winners: [], losers: [] });
    const [amount, setAmount] = useState(5);

    useEffect(() => {
        const fetchMovers = async () => {
            const data = await getWinnersAndLosers(amount);
            setMovers({ winners: data.winners, losers: data.losers });
        }

        fetchMovers();

    }, [amount]);

    const handleFinanceDataUpdate = (data: FinanceDataStructure) => {
        setFinanceData(data);
        if (data[symbol]) {
            setSymbol(symbol);
        } else if (Object.keys(data).length > 0) {
            setSymbol(Object.keys(data)[0]);
        }
    };

  return (
      <Router>
          <div className="App">
              <Navbar/>
              <FinanceData onFinanceDataUpdate={handleFinanceDataUpdate} />
              <MarketMovers movers={movers} handleLoadMore={() => setAmount(amount + 5)} />
              <News />
          </div>
      </Router>
  );
}

export default App;
