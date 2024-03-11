import React, {useState} from 'react';
import { BrowserRouter as Router } from 'react-router-dom';
import './App.css';
import Navbar from "./components/navbar/Navbar";
import MarketMovers from "./components/market_movers/MarketMovers";
import FinanceData, {FinanceDataStructure, symbols} from "./service/FinanceData";


function App() {
    const [symbol, setSymbol] = useState(symbols[0]);
    const [financeData, setFinanceData] = useState<FinanceDataStructure>({});

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
              <MarketMovers symbol={symbol} />
          </div>
      </Router>
  );
}

export default App;
