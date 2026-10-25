import { useState } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import TimeWindow, { type TimeSelection } from "./TimeWindow";

const data = Array.from({ length: 80 }, (_, i) => ({
  date: new Date(Date.UTC(2026, 0, i + 1)).toISOString().slice(0, 10),
  close: 100 + i,
}));

function Harness({ rows = data }: { rows?: typeof data }) {
  const [selection, setSelection] = useState<TimeSelection | null>(null);

  return (
    <>
      <TimeWindow data={rows} selection={selection} onChange={setSelection} />
      <output data-testid="selection">{JSON.stringify(selection)}</output>
    </>
  );
}

test("presets select the latest sessions without shortening the overview", () => {
  render(<Harness />);

  fireEvent.click(screen.getByText("Timeline"));

  fireEvent.click(screen.getByRole("button", { name: "20 S" }));

  expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":60,"endIndex":79}');

  expect(screen.getByRole("slider", { name: "Start of time window" })).toHaveAttribute(
    "aria-valuemin",
    "0",
  );

  fireEvent.keyDown(screen.getByRole("button", { name: "Move time window" }), { key: "Home" });

  expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":0,"endIndex":19}');

  expect(screen.getByText("CUSTOM / 20 SESSIONS")).toBeInTheDocument();

  expect(screen.getByRole("button", { name: "20 S" })).toHaveAttribute("aria-pressed", "false");

  fireEvent.click(screen.getByRole("button", { name: "ALL" }));

  expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":0,"endIndex":79}');
});

test("date inputs snap inward and reject reversed ranges", () => {
  render(
    <Harness
      rows={[
        { date: "2026-10-02", close: 1 },
        { date: "2026-10-05", close: 2 },
        { date: "2026-10-06", close: 3 },
      ]}
    />,
  );

  fireEvent.change(screen.getByLabelText("Start date"), {
    target: { value: "2026-10-03" },
  });

  fireEvent.blur(screen.getByLabelText("Start date"));

  expect(screen.getByLabelText("Start date")).toHaveValue("2026-10-05");

  fireEvent.change(screen.getByLabelText("End date"), {
    target: { value: "2026-10-02" },
  });

  fireEvent.blur(screen.getByLabelText("End date"));

  expect(screen.getByRole("alert")).toBeInTheDocument();

  expect(screen.getByLabelText("End date")).toHaveValue("2026-10-06");

  expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":1,"endIndex":2}');
});

test("handles cannot cross and window movement clamps at history bounds", () => {
  render(<Harness />);

  fireEvent.click(screen.getByText("Timeline"));

  const start = screen.getByRole("slider", {
    name: "Start of time window",
  });

  fireEvent.keyDown(start, { key: "End" });
  fireEvent.keyDown(start, { key: "ArrowRight" });

  expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":79,"endIndex":79}');

  fireEvent.keyDown(screen.getByRole("button", { name: "Move time window" }), {
    key: "ArrowRight",
  });

  expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":79,"endIndex":79}');
});

test("pointer dragging moves the window without changing its length", () => {
  vi.stubGlobal("PointerEvent", MouseEvent);

  try {
    const { container } = render(<Harness />);

    fireEvent.click(screen.getByText("Timeline"));

    const track = container.querySelector(".time-track") as HTMLElement;

    vi.spyOn(track, "getBoundingClientRect").mockReturnValue({
      width: 790,
      left: 0,
    } as DOMRect);

    const center = screen.getByRole("button", {
      name: "Move time window",
    });

    center.setPointerCapture = vi.fn();

    fireEvent.click(screen.getByRole("button", { name: "20 S" }));

    fireEvent.pointerDown(center, {
      button: 0,
      clientX: 600,
    });

    fireEvent.pointerMove(track, { clientX: 500 });

    expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":50,"endIndex":69}');

    fireEvent.pointerUp(track);
    fireEvent.pointerMove(track, { clientX: 400 });

    expect(screen.getByTestId("selection")).toHaveTextContent('{"startIndex":50,"endIndex":69}');
  } finally {
    vi.unstubAllGlobals();
  }
});

test("empty and single-session histories remain usable", () => {
  const { rerender } = render(<Harness rows={[]} />);

  expect(screen.queryByRole("region", { name: "Time window" })).not.toBeInTheDocument();

  rerender(<Harness rows={[data[0]]} />);

  fireEvent.click(screen.getByRole("button", { name: "20 S" }));

  expect(screen.getByText("1 SESSIONS")).toBeInTheDocument();
});

test("overview starts collapsed while date controls stay visible", () => {
  const { container } = render(<Harness />);
  const track = container.querySelector(".time-track");

  expect(track).not.toBeVisible();
  expect(screen.getByLabelText("Start date")).toBeVisible();
  expect(screen.getByLabelText("End date")).toBeVisible();

  fireEvent.click(screen.getByText("Timeline"));
  expect(track).toBeVisible();

  fireEvent.click(screen.getByText("Timeline"));
  expect(track).not.toBeVisible();
});
