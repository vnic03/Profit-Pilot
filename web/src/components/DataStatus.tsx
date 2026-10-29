import React, { useEffect, useState } from "react";
import { getDataStatus } from "../service/financeService";
import ImportBudget from "./ImportBudget";
import OperationsStatus from "./OperationsStatus";
import type { StatusRow } from "../service/apiTypes";

interface LoadResult {
  key: string;
  rows: StatusRow[];
  error: boolean;
}

function formatTimestamp(value: string | null): string {
  if (!value) {
    return "Never";
  }

  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Berlin",
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function getFreshnessState(row: StatusRow): {
  label: string;
  tone: string;
} {
  switch (row.freshness) {
    case "CURRENT":
      return { label: "Current", tone: "success" };

    case "BEHIND_REFERENCE":
      return { label: "Check needed", tone: "warning" };

    case "MISSING":
      return { label: "No stored prices", tone: "neutral" };

    case "FUTURE_DATE":
      return { label: "Unexpected future date", tone: "warning" };

    case "CALENDAR_UNAVAILABLE":
      return { label: "Calendar update required", tone: "warning" };

    default:
      return { label: "Unknown", tone: "neutral" };
  }
}

function getImportState(row: StatusRow): {
  label: string;
  tone: string;
} {
  if (!row.lastAttemptAt) {
    return {
      label: "No attempt recorded",
      tone: "neutral",
    };
  }

  const attempt = Date.parse(row.lastAttemptAt);
  const success = row.lastSuccessAt ? Date.parse(row.lastSuccessAt) : null;

  if (!Number.isFinite(attempt) || (success !== null && !Number.isFinite(success))) {
    return {
      label: "Unknown status",
      tone: "neutral",
    };
  }

  if (success !== null && success >= attempt) {
    return {
      label: "Last attempt confirmed",
      tone: "success",
    };
  }

  return {
    label: "Last attempt not confirmed",
    tone: "warning",
  };
}

const DataStatus: React.FC<{ refreshVersion: number }> = ({ refreshVersion }) => {
  const [retryVersion, setRetryVersion] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  const requestKey = `${refreshVersion}:${retryVersion}`;

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const rows: StatusRow[] = await getDataStatus();

        if (!cancelled) {
          setResult({ key: requestKey, rows, error: false });
        }
      } catch {
        if (!cancelled) {
          setResult({ key: requestKey, rows: [], error: true });
        }
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [requestKey]);

  const current = result?.key === requestKey ? result : null;

  return (
    <section aria-label="Stored data status">
      <h2>Data status</h2>
      <OperationsStatus refreshVersion={refreshVersion} />
      <ImportBudget refreshVersion={refreshVersion} />
      <p>Import timestamps are shown in Europe/Berlin time.</p>
      <p className="data-status-explanation">
        Freshness compares the latest stored price date with the expected trading session, using the
        configured US market holiday calendar. A session becomes due at 06:00 America/New_York the
        following day. Early-close sessions count as trading days. “Check needed” may indicate
        missing prices or a provider delay. A confirmed import does not guarantee a newer price
        date. Historical gaps are not checked.
      </p>

      {!current && <p role="status">Loading data status...</p>}

      {current?.error && (
        <div role="alert">
          <p>Could not load data status.</p>
          <button onClick={() => setRetryVersion((value) => value + 1)}>Retry status</button>
        </div>
      )}

      {current &&
        !current.error &&
        (current.rows.length === 0 ? (
          <p>No tracked stocks available.</p>
        ) : (
          <div
            className="table-scroll"
            role="region"
            aria-label="Stored price coverage label"
            tabIndex={0}
          >
            <table>
              <thead>
                <tr>
                  <th scope="col">Symbol</th>
                  <th scope="col">Stored prices</th>
                  <th scope="col">Latest price date</th>
                  <th scope="col">Price freshness</th>
                  <th scope="col">Import status</th>
                  <th scope="col">Last attempt</th>
                  <th scope="col">Last successful import</th>
                </tr>
              </thead>
              <tbody>
                {current.rows.map((row) => {
                  const importState = getImportState(row);
                  const freshnessState = getFreshnessState(row);

                  return (
                    <tr key={row.symbol}>
                      <th scope="row">{row.symbol}</th>
                      <td>{row.priceCount}</td>
                      <td>{row.latestPriceDate ?? "No data"}</td>
                      <td>
                        <span className={`import-state import-state--${freshnessState.tone}`}>
                          {freshnessState.label}
                        </span>

                        <div>
                          <small>Reference: {row.referenceDate ?? "Unavailable"}</small>
                        </div>
                      </td>

                      <td>
                        <span className={`import-state import-state--${importState.tone}`}>
                          {importState.label}
                        </span>
                      </td>

                      <td>{formatTimestamp(row.lastAttemptAt)}</td>
                      <td>{formatTimestamp(row.lastSuccessAt)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ))}
    </section>
  );
};

export default DataStatus;
