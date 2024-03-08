import React from 'react';
import { BrowserRouter as Router } from 'react-router-dom';
import './App.css';
import Navbar from "./components/navbar/Navbar";
import Prototype from "./components/charts/prototype/prototype";
import MarketMovers from "./components/market_movers/MarketMovers";

function App() {
  return (
      <Router>
          <div className="App">
              <Navbar/>
              <Prototype symbol={"IBM"} />
              <MarketMovers symbol={"IBM"} amount={5} />
          </div>
      </Router>
  );
}

export default App;
