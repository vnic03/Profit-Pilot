import React, {useEffect, useState} from "react";
import {getDataStatus} from "../service/financeService";

interface StatusRow {
    symbol: string;
    priceCount: number;
    latestPriceDate: string | null;
    lastAttemptAt: string | null;
    lastSuccessAt: string | null;
}

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
        timeStyle: "short"
    }).format(new Date(value));
}

const DataStatus: React.FC<{refreshVersion: number}> = ({
    refreshVersion
}) => {
    const [retryVersion, setRetryVersion] = useState(0);
    const [result, setResult] = useState<LoadResult | null>(null);
    const requestKey = `${refreshVersion}:${retryVersion}`;

    useEffect(() => {
        let cancelled = false;

        const load = async () => {
            try {
                const rows: StatusRow[] = await getDataStatus();

                if (!cancelled) {
                    setResult({key: requestKey, rows, error: false});
                }
            } catch {
                if (!cancelled) {
                    setResult({key: requestKey, rows: [], error: true});
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
            <p>Import timestamps are shown in Europe/Berlin time.</p>

            {!current && (
                <p role="status">Loading data status...</p>
            )}

            {current?.error && (
                <div role="alert">
                    <p>Could not load data status.</p>
                    <button onClick={() => setRetryVersion(value => value + 1)}>
                        Retry status
                    </button>
                </div>
            )}

            {current && !current.error && (
                current.rows.length === 0 ? (
                    <p>No tracked stocks available.</p>
                ) : (
                    <div style={{overflowX: "auto"}}>
                        <table>
                            <thead>
                                <tr>
                                    <th scope="col">Symbol</th>
                                    <th scope="col">Stored prices</th>
                                    <th scope="col">Latest price date</th>
                                    <th scope="col">Last attempt</th>
                                    <th scope="col">Last successful import</th>
                                </tr>
                            </thead>
                            <tbody>
                                {current.rows.map(row => (
                                    <tr key={row.symbol}>
                                        <th scope="row">{row.symbol}</th>
                                        <td>{row.priceCount}</td>
                                        <td>{row.latestPriceDate ?? "No data"}</td>
                                        <td>{formatTimestamp(row.lastAttemptAt)}</td>
                                        <td>{formatTimestamp(row.lastSuccessAt)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )
            )}
        </section>
    );
};

export default DataStatus;
