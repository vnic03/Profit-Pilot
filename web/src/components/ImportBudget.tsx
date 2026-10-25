import React, { useEffect, useId, useState } from "react";
import { getImportBudget } from "../service/financeService";
import { getNewsBudget } from "../service/newsService";

interface Budget {
  provider: string;
  requestLimit: number;
  reservedRequests: number;
  remainingRequests: number;
  window: string;
}

interface LoadResult {
  key: string;
  budget: Budget | null;
  loadedAt: string | null;
  error: string | null;
}

interface ImportBudgetProps {
  refreshVersion: number;
  provider?: "prices" | "news";
}

const ImportBudget: React.FC<ImportBudgetProps> = ({ refreshVersion, provider = "prices" }) => {
  const titleId = useId();
  const [retryVersion, setRetryVersion] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);

  const requestKey = `${provider}:${refreshVersion}:${retryVersion}`;
  const title = provider === "prices" ? "Price import budget" : "News request budget";
  const providerName = provider === "prices" ? "Alpha Vantage" : "Marketaux";

  useEffect(() => {
    const controller = new AbortController();

    const load = async () => {
      try {
        const loader = provider === "prices" ? getImportBudget : getNewsBudget;

        const budget: Budget = await loader(controller.signal);

        if (!controller.signal.aborted) {
          setResult({
            key: requestKey,
            budget,
            loadedAt: new Date().toISOString(),
            error: null,
          });
        }
      } catch {
        if (!controller.signal.aborted) {
          setResult({
            key: requestKey,
            budget: null,
            loadedAt: null,
            error: "Could not load the request budget.",
          });
        }
      }
    };

    void load();

    return () => controller.abort();
  }, [provider, requestKey]);

  const current = result?.key === requestKey ? result : null;
  const budget = current?.budget;

  return (
    <section className="import-budget" aria-labelledby={titleId}>
      <div className="budget-heading">
        <div>
          <h3 id={titleId}>{title}</h3>
          <p>{providerName} · Shared across all tracked stocks</p>
        </div>

        <button
          type="button"
          onClick={() => setRetryVersion((value) => value + 1)}
          disabled={!current}
        >
          {current?.error ? "Retry budget" : "Refresh budget"}
        </button>
      </div>

      {!current && <p role="status">Loading budget...</p>}

      {current?.error && <p role="alert">{current.error}</p>}

      {budget && (
        <>
          <div className="budget-metrics">
            <div>
              <span className="budget-value">{budget.remainingRequests}</span>
              <span className="budget-label">Available</span>
            </div>

            <div>
              <span className="budget-value">{budget.reservedRequests}</span>
              <span className="budget-label">Reserved in the last 24 hours</span>
            </div>

            <div>
              <span className="budget-value">{budget.requestLimit}</span>
              <span className="budget-label">Local limit per 24 hours</span>
            </div>
          </div>

          <meter
            className="budget-meter"
            min={0}
            max={budget.requestLimit}
            value={Math.min(budget.reservedRequests, budget.requestLimit)}
            aria-label={`${providerName} reserved request slots`}
          >
            {budget.reservedRequests} of {budget.requestLimit}
          </meter>

          {budget.remainingRequests === 0 && (
            <p className="budget-notice" role="status">
              No request slots are currently available. Slots become available as reservations leave
              the rolling 24-hour window.
            </p>
          )}

          <p className="budget-explanation">
            {provider === "news"
              ? "Cached articles do not require a new provider request. "
              : "Current stocks are skipped without a provider request. "}
            Failed requests may retain a reservation. Requests outside Profit Pilot are not
            included.
          </p>

          <p className="budget-explanation">
            Refreshing this budget does not contact the provider.
          </p>

          {current?.loadedAt && (
            <p className="budget-updated">
              Budget checked:{" "}
              <time dateTime={current.loadedAt}>
                {new Intl.DateTimeFormat("en-GB", {
                  timeZone: "Europe/Berlin",
                  dateStyle: "medium",
                  timeStyle: "short",
                }).format(new Date(current.loadedAt))}
              </time>{" "}
              · Europe/Berlin
            </p>
          )}
        </>
      )}
    </section>
  );
};

export default ImportBudget;
