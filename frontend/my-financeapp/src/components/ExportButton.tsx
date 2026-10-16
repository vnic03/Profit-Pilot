import React, {useEffect, useRef, useState} from "react";
import {getArffExport} from "../service/financeService";

interface ExportButtonProps {
    symbol: string;
}

const ExportButton: React.FC<ExportButtonProps> = ({symbol}) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);

    const activeRequest = useRef<AbortController | null>(null);

    useEffect(() => {
        return () => {
            activeRequest.current?.abort();
        };
    }, []);

    const download = async () => {
        if (activeRequest.current) {
            return;
        }

        const controller = new AbortController();
        activeRequest.current = controller;

        setLoading(true);
        setError(null);
        setMessage(null);

        try {
            const blob: Blob = await getArffExport(
                symbol,
                controller.signal
            );

            if (controller.signal.aborted) {
                return;
            }

            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");

            link.href = url;
            link.download = `${symbol}-indicators.arff`;

            try {
                document.body.appendChild(link);
                link.click();
            } finally {
                link.remove();

                // Give the browser time to start reading the blob before releasing it.
                window.setTimeout(() => {
                    URL.revokeObjectURL(url);
                }, 30000);
            }

            setMessage(`Download started for ${symbol}.`);
        } catch (cause: unknown) {
            if (!controller.signal.aborted) {
                setError(
                    cause instanceof Error
                        ? cause.message
                        : "Could not download the export."
                );
            }
        } finally {
            if (activeRequest.current === controller) {
                activeRequest.current = null;
            }

            if (!controller.signal.aborted) {
                setLoading(false);
            }
        }
    };

    return (
        <div>
            <button
                type="button"
                onClick={download}
                disabled={loading}
                aria-busy={loading}
            >
                {loading
                    ? `Preparing ${symbol} export...`
                    : `Download ${symbol} indicators (.arff)`}
            </button>

            <p>
                Export settings: SMA 20, EMA 20, RSI 14,
                MACD 12/26/9. Independent of chart settings.
            </p>

            {error && <p role="alert">{error}</p>}
            {message && <p role="status">{message}</p>}
        </div>
    );
};

export default ExportButton;
