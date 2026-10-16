import React, {useEffect, useId, useState} from "react";

export type SeriesKey =
    | "close"
    | "open"
    | "high"
    | "low"
    | "volume"
    | "SMA"
    | "EMA"
    | "RSI"
    | "MACD";

interface PeriodControlProps {
    name: string;
    value: number;
    onChange: (value: number) => void;
}

const PeriodControl: React.FC<PeriodControlProps> = ({
    name,
    value,
    onChange
}) => {
    const id = useId();
    const [draft, setDraft] = useState(String(value));

    useEffect(() => {
        setDraft(String(value));
    }, [value]);

    const parsed = Number(draft);
    const valid = draft.trim() !== ""
        && Number.isInteger(parsed)
        && parsed >= 1
        && parsed <= 50;

    const commit = () => {
        if (valid) {
            onChange(parsed);
            setDraft(String(parsed));
        } else {
            setDraft(String(value));
        }
    };

    return (
        <div className="period-control">
            <label htmlFor={`${id}-number`}>{name} period</label>

            <input
                id={`${id}-range`}
                type="range"
                min={1}
                max={50}
                step={1}
                value={value}
                aria-label={`${name} period slider`}
                onChange={event => onChange(Number(event.target.value))}
            />

            <input
                id={`${id}-number`}
                type="number"
                min={1}
                max={50}
                step={1}
                inputMode="numeric"
                value={draft}
                aria-invalid={!valid}
                aria-describedby={`${id}-hint`}
                onChange={event => setDraft(event.target.value)}
                onBlur={commit}
                onKeyDown={event => {
                    if (event.key === "Enter") {
                        event.preventDefault();
                        commit();
                    }
                }}
            />

            <small id={`${id}-hint`}>
                {valid
                    ? "1–50 trading periods. Press Enter or leave the field to apply."
                    : "Enter a whole number from 1 to 50."}
            </small>
        </div>
    );
};

interface ChartControlsProps {
    visible: Record<SeriesKey, boolean>;
    onToggle: (key: SeriesKey) => void;
    smaPeriod: number;
    emaPeriod: number;
    rsiPeriod: number;
    onSmaChange: (value: number) => void;
    onEmaChange: (value: number) => void;
    onRsiChange: (value: number) => void;
}

const groups: {
    title: string;
    items: {key: SeriesKey; label: string}[];
}[] = [
    {
        title: "Price series",
        items: [
            {key: "close", label: "Close"},
            {key: "open", label: "Open"},
            {key: "high", label: "High"},
            {key: "low", label: "Low"}
        ]
    },
    {
        title: "Indicators and volume",
        items: [
            {key: "SMA", label: "SMA"},
            {key: "EMA", label: "EMA"},
            {key: "RSI", label: "RSI"},
            {key: "MACD", label: "MACD"},
            {key: "volume", label: "Volume"}
        ]
    }
];

const ChartControls: React.FC<ChartControlsProps> = ({
    visible,
    onToggle,
    smaPeriod,
    emaPeriod,
    rsiPeriod,
    onSmaChange,
    onEmaChange,
    onRsiChange
}) => (
    <div className="chart-controls">
        {groups.map(group => (
            <fieldset className="chart-control-group" key={group.title}>
                <legend>{group.title}</legend>

                <div className="chart-toggle-list">
                    {group.items.map(item => (
                        <button
                            key={item.key}
                            type="button"
                            aria-pressed={visible[item.key]}
                            onClick={() => onToggle(item.key)}
                        >
                            {item.label}
                            <span aria-hidden="true">
                                {visible[item.key] ? " On" : " Off"}
                            </span>
                        </button>
                    ))}
                </div>
            </fieldset>
        ))}

        <div className="period-controls">
            {visible.SMA && (
                <PeriodControl
                    name="SMA"
                    value={smaPeriod}
                    onChange={onSmaChange}
                />
            )}

            {visible.EMA && (
                <PeriodControl
                    name="EMA"
                    value={emaPeriod}
                    onChange={onEmaChange}
                />
            )}

            {visible.RSI && (
                <PeriodControl
                    name="RSI"
                    value={rsiPeriod}
                    onChange={onRsiChange}
                />
            )}
        </div>

        <details className="chart-help">
            <summary>Chart guide</summary>
            <dl>
                <dt>Price series</dt>
                <dd>Daily open, high, low and closing prices.</dd>

                <dt>SMA and EMA</dt>
                <dd>
                    Moving averages calculated from stored closing prices.
                    The period controls how many observations are used.
                </dd>

                <dt>RSI</dt>
                <dd>
                    A separate indicator chart with a scale from 0 to 100.
                </dd>

                <dt>MACD</dt>
                <dd>
                    A separate chart showing MACD, its signal line and
                    histogram, using periods 12, 26 and 9.
                </dd>

                <dt>Volume</dt>
                <dd>
                    Trading volume uses the right-hand axis of the main chart.
                </dd>

                <dt>History</dt>
                <dd>
                    Indicators need an initial calculation period.
                    Missing early values are expected.
                    Time ranges select stored observations, not calendar days.
                </dd>
            </dl>
        </details>
    </div>
);

export default ChartControls;
