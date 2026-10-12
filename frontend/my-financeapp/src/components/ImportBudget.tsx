import React, {useEffect, useState} from "react";
import {getImportBudget} from "../service/financeService";

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

const ImportBudget: React.FC<{refreshVersion: number}> = ({
    refreshVersion
}) => {
    const [retryVersion, setRetryVersion] = useState(0);
    const [result, setResult] = useState<LoadResult | null>(null);

    const requestKey = `${refreshVersion}:${retryVersion}`;

    useEffect(() => {
        const controller = new AbortController();

        const load = async () => {
            try {
                const budget: Budget =
                    await getImportBudget(controller.signal);

                if (!controller.signal.aborted) {
                    setResult({
                        key: requestKey,
                        budget,
                        loadedAt: new Date().toISOString(),
                        error: null
                    });
                }
            } catch {
                if (!controller.signal.aborted) {
                    setResult({
                        key: requestKey,
                        budget: null,
                        loadedAt: null,
                        error: "Could not load the import budget."
                    });
                }
            }
        };

        void load();

        return () => controller.abort();
    }, [requestKey]);

    const current = result?.key === requestKey ? result : null;
    const budget = current?.budget;

    const reload = () => setRetryVersion(value => value + 1);

    return (
        <section className="import-budget" aria-labelledby="budget-title">
            <div className="budget-heading">
                <div>
                    <h3 id="budget-title">Price import budget</h3>
                    <p>Alpha Vantage · Shared across all tracked stocks</p>
                </div>

                <button
                    type="button"
                    onClick={reload}
                    disabled={!current}
                >
                    Refresh budget
                </button>
            </div>

            {!current && (
                <p role="status">Loading import budget...</p>
            )}

            {current?.error && (
                <p role="alert">{current.error}</p>
            )}

            {budget && (
                <>
                    <div className="budget-metrics">
                        <div>
                            <span className="budget-value">
                                {budget.remainingRequests}
                            </span>
                            <span className="budget-label">
                                Available slots
                            </span>
                        </div>

                        <div>
                            <span className="budget-value">
                                {budget.reservedRequests}
                            </span>
                            <span className="budget-label">
                                Reserved in the last 24 hours
                            </span>
                        </div>

                        <div>
                            <span className="budget-value">
                                {budget.requestLimit}
                            </span>
                            <span className="budget-label">
                                Local limit per 24 hours
                            </span>
                        </div>
                    </div>

                    <meter
                        className="budget-meter"
                        min={0}
                        max={budget.requestLimit}
                        value={Math.min(
                            budget.reservedRequests,
                            budget.requestLimit
                        )}
                        aria-label="Reserved import slots"
                    >
                        {budget.reservedRequests} of {budget.requestLimit}
                    </meter>

                    {budget.remainingRequests === 0 && (
                        <p className="budget-notice" role="status">
                            No import slots are currently available.
                            Slots become available as reservations
                            leave the rolling 24-hour window.
                        </p>
                    )}

                    <p className="budget-explanation">
                        Failed or interrupted imports may retain a reservation.
                        Requests made outside Profit Pilot are not included.
                    </p>

                    {current?.loadedAt && (
                        <p className="budget-updated">
                            Budget checked:{" "}
                            <time dateTime={current.loadedAt}>
                                {new Intl.DateTimeFormat("en-GB", {
                                    timeZone: "Europe/Berlin",
                                    dateStyle: "medium",
                                    timeStyle: "short"
                                }).format(new Date(current.loadedAt))}
                            </time>
                            {" "}· Europe/Berlin
                        </p>
                    )}
                </>
            )}
        </section>
    );
};

export default ImportBudget;
