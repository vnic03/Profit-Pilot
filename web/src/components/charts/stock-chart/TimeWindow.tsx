import { useEffect, useId, useMemo, useRef, useState } from "react";
import type { KeyboardEvent, PointerEvent } from "react";
import "./TimeWindow.css";

export interface TimeSelection {
  startIndex: number;
  endIndex: number;
}

interface Props {
  data: { date: string; close: number }[];
  selection: TimeSelection | null;
  onChange: (selection: TimeSelection | null) => void;
}

type Mode = "start" | "end" | "move";

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

export default function TimeWindow({ data, selection, onChange }: Props) {
  const id = useId();
  const track = useRef<HTMLDivElement>(null);

  const drag = useRef<{
    pointerId: number;
    x: number;
    width: number;
    mode: Mode;
    startIndex: number;
    endIndex: number;
  } | null>(null);

  const [dragging, setDragging] = useState(false);
  const [preset, setPreset] = useState("ALL");
  const [error, setError] = useState("");

  const last = Math.max(0, data.length - 1);
  const start = clamp(selection?.startIndex ?? 0, 0, last);
  const end = clamp(selection?.endIndex ?? last, start, last);

  const from = data[start]?.date ?? "";
  const to = data[end]?.date ?? "";

  const [draftFrom, setDraftFrom] = useState(from);
  const [draftTo, setDraftTo] = useState(to);

  useEffect(() => {
    setDraftFrom(from);
    setDraftTo(to);
  }, [from, to]);

  const path = useMemo(() => {
    const values = data.map((row) => row.close).filter(Number.isFinite);

    if (!values.length) return "";

    const low = values.reduce((a, b) => Math.min(a, b));
    const high = values.reduce((a, b) => Math.max(a, b));

    let penDown = false;

    return data
      .map((row, index) => {
        if (!Number.isFinite(row.close)) {
          penDown = false;
          return "";
        }

        const x = data.length === 1 ? 500 : (index / (data.length - 1)) * 1000;

        const y = high === low ? 32 : 55 - ((row.close - low) / (high - low)) * 46;

        const command = penDown ? "L" : "M";
        penDown = true;

        return `${command}${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(" ");
  }, [data]);

  if (!data.length) return null;

  const left = last ? (start / last) * 100 : 0;
  const right = last ? (end / last) * 100 : 100;

  const apply = (next: TimeSelection, label = "CUSTOM") => {
    setPreset(label);
    setError("");
    setDraftFrom(data[next.startIndex].date);
    setDraftTo(data[next.endIndex].date);
    onChange(next);
  };

  const choosePreset = (size: number | null) => {
    apply(
      {
        startIndex: size ? Math.max(0, data.length - size) : 0,
        endIndex: last,
      },
      size ? `${size} S` : "ALL",
    );
  };

  const begin = (event: PointerEvent<HTMLButtonElement>, mode: Mode) => {
    if (event.button !== 0 || !last || drag.current) return;

    const bounds = track.current?.getBoundingClientRect();
    if (!bounds?.width) return;

    event.preventDefault();
    event.currentTarget.focus();
    event.currentTarget.setPointerCapture(event.pointerId);

    drag.current = {
      pointerId: event.pointerId,
      x: event.clientX,
      width: bounds.width,
      mode,
      startIndex: start,
      endIndex: end,
    };

    setDragging(true);
  };

  const move = (event: PointerEvent<HTMLDivElement>) => {
    const active = drag.current;

    if (!active || event.pointerId !== active.pointerId) return;

    const delta = Math.round(((event.clientX - active.x) / active.width) * last);

    if (active.mode === "start") {
      apply({
        startIndex: clamp(active.startIndex + delta, 0, active.endIndex),
        endIndex: active.endIndex,
      });
    } else if (active.mode === "end") {
      apply({
        startIndex: active.startIndex,
        endIndex: clamp(active.endIndex + delta, active.startIndex, last),
      });
    } else {
      const shift = clamp(delta, -active.startIndex, last - active.endIndex);

      apply({
        startIndex: active.startIndex + shift,
        endIndex: active.endIndex + shift,
      });
    }
  };

  const stop = (event: PointerEvent<HTMLDivElement>) => {
    if (drag.current?.pointerId !== event.pointerId) return;

    drag.current = null;
    setDragging(false);
  };

  const keyboard = (event: KeyboardEvent<HTMLButtonElement>, mode: Mode) => {
    const step = event.shiftKey ? 10 : 1;

    const direction = ["ArrowRight", "ArrowUp"].includes(event.key)
      ? step
      : ["ArrowLeft", "ArrowDown"].includes(event.key)
        ? -step
        : 0;

    if (!direction && event.key !== "Home" && event.key !== "End") {
      return;
    }

    event.preventDefault();

    if (mode === "move") {
      const shift =
        event.key === "Home"
          ? -start
          : event.key === "End"
            ? last - end
            : clamp(direction, -start, last - end);

      apply({
        startIndex: start + shift,
        endIndex: end + shift,
      });
    } else if (mode === "start") {
      apply({
        startIndex:
          event.key === "Home" ? 0 : event.key === "End" ? end : clamp(start + direction, 0, end),
        endIndex: end,
      });
    } else {
      apply({
        startIndex: start,
        endIndex:
          event.key === "Home"
            ? start
            : event.key === "End"
              ? last
              : clamp(end + direction, start, last),
      });
    }
  };

  const commitDate = (mode: "start" | "end", value: string) => {
    if (value === (mode === "start" ? from : to)) return;

    // Start snaps forward; end snaps backward to a stored session.
    const index =
      mode === "start"
        ? data.findIndex((row) => row.date >= value)
        : data.reduce((found, row, i) => (row.date <= value ? i : found), -1);

    if (
      !value ||
      value < data[0].date ||
      value > data[last].date ||
      index < 0 ||
      (mode === "start" ? index > end : index < start)
    ) {
      setError("Choose a date within the stored history. FROM must not be after TO.");
      setDraftFrom(from);
      setDraftTo(to);
      return;
    }

    apply({
      startIndex: mode === "start" ? index : start,
      endIndex: mode === "end" ? index : end,
    });
  };

  const ticks = [...new Set([0, Math.round(last / 3), Math.round((last * 2) / 3), last])];

  return (
    <section className="time-window" aria-label="Time window">
      <div className="time-window-toolbar">
        <span className="time-window-title">TIME RANGE</span>

        <div className="time-presets" role="group" aria-label="History range">
          {[20, 60, null].map((size) => {
            const label = size ? `${size} S` : "ALL";

            return (
              <button
                key={label}
                type="button"
                aria-pressed={preset === label}
                onClick={() => choosePreset(size)}
              >
                {label}
              </button>
            );
          })}
        </div>

        <span className="time-window-count">
          {preset === "CUSTOM" && "CUSTOM / "}
          {end - start + 1} SESSIONS
        </span>

        <div className="time-dates">
          {(["start", "end"] as const).map((mode) => (
            <label key={mode}>
              {mode === "start" ? "FROM" : "TO"}

              <input
                type="date"
                aria-label={mode === "start" ? "Start date" : "End date"}
                min={data[0].date}
                max={data[last].date}
                aria-describedby={`${id}-hint`}
                value={mode === "start" ? draftFrom : draftTo}
                onChange={(event) =>
                  (mode === "start" ? setDraftFrom : setDraftTo)(event.target.value)
                }
                onBlur={(event) => commitDate(mode, event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.currentTarget.blur();
                  }

                  if (event.key === "Escape") {
                    setDraftFrom(from);
                    setDraftTo(to);
                    setError("");
                  }
                }}
              />
            </label>
          ))}
        </div>
      </div>

      <details className="time-window-details">
        <summary>Timeline</summary>

        <div
          className="time-track"
          ref={track}
          data-dragging={dragging}
          onPointerMove={move}
          onPointerUp={stop}
          onPointerCancel={stop}
          onLostPointerCapture={stop}
        >
          <svg viewBox="0 0 1000 64" preserveAspectRatio="none" aria-hidden="true">
            <path
              d={path}
              fill="none"
              stroke="var(--blue)"
              strokeWidth="1.5"
              vectorEffect="non-scaling-stroke"
            />
          </svg>

          <div className="time-mask" style={{ left: 0, width: `${left}%` }} />

          <div className="time-mask" style={{ left: `${right}%`, right: 0 }} />

          <button
            type="button"
            className="time-selection"
            aria-label="Move time window"
            title="Drag to move. Arrow keys: 1 session; Shift: 10 sessions."
            style={{
              left: `${left}%`,
              width: `${right - left}%`,
            }}
            onPointerDown={(event) => begin(event, "move")}
            onKeyDown={(event) => keyboard(event, "move")}
          />

          {(["start", "end"] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              className={`time-handle time-handle--${mode}`}
              role="slider"
              aria-orientation="horizontal"
              aria-label={mode === "start" ? "Start of time window" : "End of time window"}
              aria-valuemin={mode === "start" ? 0 : start}
              aria-valuemax={mode === "start" ? end : last}
              aria-valuenow={mode === "start" ? start : end}
              aria-valuetext={mode === "start" ? from : to}
              style={{
                left: `${mode === "start" ? left : right}%`,
              }}
              onPointerDown={(event) => begin(event, mode)}
              onKeyDown={(event) => keyboard(event, mode)}
            >
              <span aria-hidden="true">Ⅱ</span>
            </button>
          ))}
        </div>

        <div className="time-ticks" aria-hidden="true">
          {ticks.map((index) => (
            <span
              key={index}
              style={{
                left: `${last ? (index / last) * 100 : 0}%`,
              }}
            >
              {data[index].date.slice(5)}
            </span>
          ))}
        </div>

        <p id={`${id}-hint`} className="time-hint">
          Drag edges to resize · drag center to move · dates apply on Enter or blur. Non-trading
          dates snap inward to stored sessions.
        </p>
      </details>

      {error && (
        <p className="time-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
