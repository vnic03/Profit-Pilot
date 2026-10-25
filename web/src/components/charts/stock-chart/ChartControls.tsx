import { useEffect, useState } from "react";

export type SeriesKey =
  "close" | "open" | "high" | "low" | "volume" | "SMA" | "EMA" | "RSI" | "MACD";

interface Props {
  visible: Record<SeriesKey, boolean>;
  onToggle: (key: SeriesKey) => void;
  smaPeriod: number;
  emaPeriod: number;
  rsiPeriod: number;
  onSmaChange: (value: number) => void;
  onEmaChange: (value: number) => void;
  onRsiChange: (value: number) => void;
}

function Period({
  name,
  value,
  onChange,
}: {
  name: string;
  value: number;
  onChange: (value: number) => void;
}) {
  const [draft, setDraft] = useState(String(value));

  useEffect(() => setDraft(String(value)), [value]);

  const parsed = Number(draft);
  const valid = draft.trim() !== "" && Number.isInteger(parsed) && parsed >= 1 && parsed <= 50;

  const commit = () => {
    if (valid) onChange(parsed);
    setDraft(String(valid ? parsed : value));
  };

  return (
    <input
      className="period-input"
      type="number"
      min={1}
      max={50}
      step={1}
      aria-label={`${name} period`}
      aria-invalid={!valid}
      title="1–50 sessions. Enter or leave the field to apply."
      value={draft}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          commit();
        }

        if (event.key === "Escape") {
          setDraft(String(value));
        }
      }}
    />
  );
}

export default function ChartControls(props: Props) {
  const { visible, onToggle } = props;

  const items: {
    key: SeriesKey;
    label: string;
    period?: number;
    change?: (value: number) => void;
  }[] = [
    { key: "close", label: "Close" },
    { key: "open", label: "Open" },
    { key: "high", label: "High" },
    { key: "low", label: "Low" },
    {
      key: "SMA",
      label: "SMA",
      period: props.smaPeriod,
      change: props.onSmaChange,
    },
    {
      key: "EMA",
      label: "EMA",
      period: props.emaPeriod,
      change: props.onEmaChange,
    },
    {
      key: "RSI",
      label: "RSI",
      period: props.rsiPeriod,
      change: props.onRsiChange,
    },
    { key: "MACD", label: "MACD" },
    { key: "volume", label: "Volume" },
  ];

  return (
    <div className="chart-controls" role="group" aria-label="Chart signals">
      {items.map((item) => (
        <div key={item.key} className={`signal-control signal-control--${item.key}`}>
          <button aria-pressed={visible[item.key]} onClick={() => onToggle(item.key)}>
            <span className="signal-lamp" aria-hidden="true" />
            {item.label}
          </button>

          {item.change && item.period !== undefined && (
            <Period name={item.label} value={item.period} onChange={item.change} />
          )}
        </div>
      ))}
    </div>
  );
}
