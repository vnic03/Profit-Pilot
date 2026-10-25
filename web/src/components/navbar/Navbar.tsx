export type View = "analysis" | "movers" | "data" | "news";

interface Props {
    view: View;
    onNavigate: (view: View) => void;
}

const views: View[] = ["analysis", "movers", "data", "news"];

export default function Navbar({view, onNavigate}: Props) {
    return (
        <header className="instrument-header">
            <button
                className="wordmark"
                aria-label="Profit Pilot — analysis"
                onClick={() => onNavigate("analysis")}
            >
                <span>PROFIT</span>
                <span>PILOT</span>
            </button>

            <div className="instrument-label">
                SIGNAL / ANALYSIS
                <br/>
                <span>PROFIT PILOT</span>
            </div>

            <nav aria-label="Main navigation">
                {views.map(item => (
                    <button
                        key={item}
                        aria-current={view === item ? "page" : undefined}
                        onClick={() => onNavigate(item)}
                    >
                        {item}
                    </button>
                ))}
            </nav>
        </header>
    );
}
