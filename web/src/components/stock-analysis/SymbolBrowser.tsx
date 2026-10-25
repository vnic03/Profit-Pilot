import { useEffect, useMemo, useState } from "react";
import type { Stock } from "../../service/useStockCatalog";

const STORAGE_KEY = "profit-pilot:favorites:v1";

function readFavorites(): string[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "[]");

    return Array.isArray(value)
      ? value.filter((item): item is string => typeof item === "string")
      : [];
  } catch {
    return [];
  }
}

interface Props {
  stocks: Stock[];
  selected: string;
  onSelect: (symbol: string) => void;
}

export default function SymbolBrowser({ stocks, selected, onSelect }: Props) {
  const [query, setQuery] = useState("");
  const [onlyFavorites, setOnlyFavorites] = useState(false);
  const [favorites, setFavorites] = useState<string[]>(readFavorites);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(favorites));
    } catch {
      // Favorites still work for this session without storage.
    }
  }, [favorites]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();

    return stocks.filter(
      (stock) =>
        (!onlyFavorites || favorites.includes(stock.symbol)) &&
        `${stock.symbol} ${stock.name}`.toLocaleLowerCase().includes(needle),
    );
  }, [stocks, query, onlyFavorites, favorites]);

  return (
    <aside className="symbol-browser" aria-label="Symbol browser">
      <div className="symbol-tools">
        <div className="rail-caption">
          SYMBOLS <span>{stocks.length}</span>
        </div>

        <input
          type="search"
          aria-label="Search symbols"
          placeholder="Symbol / company"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />

        <div className="symbol-filters" role="group" aria-label="Symbol filter">
          <button aria-pressed={!onlyFavorites} onClick={() => setOnlyFavorites(false)}>
            ALL
          </button>

          <button aria-pressed={onlyFavorites} onClick={() => setOnlyFavorites(true)}>
            FAVORITES
          </button>
        </div>
      </div>

      <ul className="symbol-list">
        {filtered.map((stock) => (
          <li key={stock.symbol} data-active={selected === stock.symbol}>
            <button
              className="symbol-select"
              aria-label={`${stock.symbol} — ${stock.name}`}
              aria-pressed={selected === stock.symbol}
              onClick={() => onSelect(stock.symbol)}
            >
              <strong>{stock.symbol}</strong>
              <span title={stock.name}>{stock.name}</span>
            </button>

            <button
              className="favorite-toggle"
              aria-label={`Favorite ${stock.symbol}`}
              aria-pressed={favorites.includes(stock.symbol)}
              onClick={() =>
                setFavorites((current) =>
                  current.includes(stock.symbol)
                    ? current.filter((symbol) => symbol !== stock.symbol)
                    : [...current, stock.symbol],
                )
              }
            >
              <span aria-hidden="true">{favorites.includes(stock.symbol) ? "★" : "☆"}</span>
            </button>
          </li>
        ))}
      </ul>

      {filtered.length === 0 && (
        <p className="rail-empty" role="status">
          No matching symbols.
        </p>
      )}

      <div className="rail-footer">
        {filtered.length} / {stocks.length} SYMBOLS
      </div>
    </aside>
  );
}
