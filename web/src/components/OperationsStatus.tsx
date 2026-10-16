import React, {useEffect, useState} from "react";
import axios from "axios";
import {API_BASE_URL} from "../service/apiConfig";

type Run = {
    startedAt: string;
    finishedAt: string;
    status: string;
    report: {
        imported: number;
        skipped: number;
        failed: number;
        waiting: number;
    } | null;
    outcomes: Record<string, string>;
};

type Snapshot = {
    build: {
        revision: string;
        builtAt: string;
    };
    imports: {
        running: boolean;
        lastFinishedRun: Run | null;
    };
};

const labels: Record<string, string> = {
    IMPORTED: "Imported",
    SKIPPED: "Already current",
    BUDGET_OR_COOLDOWN: "Budget or retry limit / cooldown",
    WAITING_FOR_DATA: "Waiting for provider data",
    FAILED: "Import failed",
    DISABLED: "Imports disabled"
};

export default function OperationsStatus({refreshVersion}: {refreshVersion: number}) {
    const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
    const [error, setError] = useState(false);

    useEffect(() => {
        const controller = new AbortController();
        setError(false);
        setSnapshot(null);

        Promise.all([
            axios.get(`${API_BASE_URL}/api/operations/build`, {
                signal: controller.signal,
                timeout: 15000
            }),
            axios.get(`${API_BASE_URL}/api/operations/imports`, {
                signal: controller.signal,
                timeout: 15000
            })
        ])
            .then(([build, imports]) => {
                if (!controller.signal.aborted) {
                    setSnapshot({build: build.data, imports: imports.data});
                }
            })
            .catch(() => {
                if (!controller.signal.aborted) {
                    setError(true);
                }
            });

        return () => controller.abort();
    }, [refreshVersion]);

    if (error) {
        return (
            <p role="alert">
                Could not load operational status. Use Reload stored data to retry.
            </p>
        );
    }

    if (!snapshot) {
        return <p role="status">Loading operational status…</p>;
    }

    const run = snapshot.imports.lastFinishedRun;

    return (
        <section aria-label="Import operation status">
            <h3>Import operation</h3>
            <p>Build: {snapshot.build.revision} · Built: {snapshot.build.builtAt}</p>
            <p>{snapshot.imports.running ? "Import running" : "No import currently running"}</p>

            {!run ? (
                <p>No finished run recorded yet.</p>
            ) : (
                <>
                    <p>
                        Last finished run: {new Date(run.finishedAt).toLocaleString()} · {run.status}
                    </p>
                    {run.report && (
                        <p>
                            Imported: {run.report.imported} · Skipped: {run.report.skipped} ·
                            Failed: {run.report.failed} · Waiting: {run.report.waiting}
                        </p>
                    )}
                    <ul>
                        {Object.entries(run.outcomes).map(([symbol, outcome]) => (
                            <li key={symbol}>
                                {symbol}: {labels[outcome] ?? outcome}
                            </li>
                        ))}
                    </ul>
                </>
            )}

            <p>
                Latest finished run is retained after a restart. A terminated run may have no final report.
                Per-stock history is shown below. Refresh to update this snapshot.
            </p>
        </section>
    );
}
