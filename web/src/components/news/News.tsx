import React, { useEffect, useRef, useState, useCallback } from "react";
import axios from "axios";
import { getNews } from "../../service/newsService";
import type { Stock } from "../../service/useStockCatalog";
import { useViewportVisibility } from "../../service/useViewportVisibility";
import ImportBudget from "../ImportBudget";

interface NewsArticle {
  title: string;
  publicationDate: string;
  description: string;
  url: string;
  imageUrl: string | null;
  source: string;
}

interface NewsResponse {
  status: "disabled" | "ready";
  articles: NewsArticle[];
  fetchedAt: string | null;
}

type NewsState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "disabled" }
  | { status: "error" }
  | { status: "limited" }
  | {
      status: "ready";
      articles: NewsArticle[];
      fetchedAt: string | null;
    };

function formatDate(value: string | null): string {
  if (!value) return "Unknown";

  const date = new Date(value);

  if (!Number.isFinite(date.getTime())) return "Unknown";

  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Berlin",
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

const News: React.FC<{ stock: Stock | null }> = ({ stock }) => {
  const [state, setState] = useState<NewsState>({ status: "idle" });
  const [budgetVersion, setBudgetVersion] = useState(0);
  const activeRequest = useRef<AbortController | null>(null);
  const { elementRef, visible } = useViewportVisibility();
  const symbol = stock?.symbol ?? "";

  useEffect(() => {
    return () => {
      activeRequest.current?.abort();
      activeRequest.current = null;
    };
  }, []);

  const loadNews = useCallback(async () => {
    if (!symbol || activeRequest.current) return;

    const controller = new AbortController();
    activeRequest.current = controller;
    setState({ status: "loading" });

    try {
      const response: NewsResponse = await getNews(symbol, controller.signal);

      if (controller.signal.aborted) return;

      if (response.status === "disabled") {
        setState({ status: "disabled" });
      } else if (response.status === "ready" && Array.isArray(response.articles)) {
        setState({
          status: "ready",
          articles: response.articles,
          fetchedAt: response.fetchedAt,
        });
      } else {
        setState({ status: "error" });
      }
    } catch (error: unknown) {
      if (!controller.signal.aborted) {
        const budgetExhausted =
          axios.isAxiosError(error) &&
          error.response?.status === 429 &&
          error.response?.data?.status === "budget_exhausted";

        setState({
          status: budgetExhausted ? "limited" : "error",
        });
      }
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
      }
      if (!controller.signal.aborted) {
        setBudgetVersion((value) => value + 1);
      }
    }
  }, [symbol]);

  useEffect(() => {
    if (!symbol || !visible || state.status !== "idle") {
      return;
    }

    // Avoid spending news requests when the user only scrolls past this section.
    const timer = window.setTimeout(() => {
      void loadNews();
    }, 600);

    return () => window.clearTimeout(timer);
  }, [symbol, visible, state.status, loadNews]);

  return (
    <section ref={elementRef} aria-labelledby="company-news-title">
      <div className="company-news-heading">
        <div>
          <h2 id="company-news-title">{stock ? `News for ${stock.name}` : "Company news"}</h2>

          <p>
            {stock
              ? `Articles mentioning ${stock.symbol}.`
              : "Select a stock in the analysis section."}
          </p>
        </div>

        {stock && (
          <button
            type="button"
            onClick={() => void loadNews()}
            disabled={state.status === "loading"}
          >
            {state.status === "loading"
              ? "Loading..."
              : state.status === "error"
                ? "Retry news"
                : state.status === "idle"
                  ? "Load now"
                  : "Check news"}
          </button>
        )}
      </div>

      {stock && state.status === "idle" && (
        <p>News loads automatically when this section is visible. You can also load it manually.</p>
      )}

      {state.status === "loading" && <p role="status">Loading company news...</p>}

      {state.status === "disabled" && (
        <p role="status">
          News is currently disabled. Stock prices and indicators remain available.
        </p>
      )}

      {state.status === "error" && <p role="alert">Could not load news. Please try again later.</p>}

      {state.status === "limited" && (
        <p role="status">
          The news request budget is currently exhausted. Please try again later. Price imports use
          a separate budget.
        </p>
      )}

      {state.status === "ready" && (
        <>
          <p className="news-fetch-time">
            Last provider fetch: {formatDate(state.fetchedAt)} · Europe/Berlin
          </p>

          {state.articles.length === 0 ? (
            <p>No matching articles were returned.</p>
          ) : (
            <ul className="company-news-list">
              {state.articles.map((article) => (
                <li key={article.url} className="news-card">
                  <div className="news-card-meta">
                    <span>{article.source}</span>
                    <span>{formatDate(article.publicationDate)}</span>
                  </div>

                  <h3>
                    <a href={article.url} target="_blank" rel="noopener noreferrer">
                      {article.title}
                    </a>
                  </h3>

                  {article.description && <p>{article.description}</p>}

                  <a
                    className="news-read-link"
                    href={article.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Read original article ↗
                  </a>
                </li>
              ))}
            </ul>
          )}

          <p className="news-footnote">
            Times are shown in Europe/Berlin. News may be served from a 30-minute cache. Checking
            again does not force a new provider request.
          </p>
        </>
      )}

      <ImportBudget provider="news" refreshVersion={budgetVersion} />
    </section>
  );
};

export default News;
