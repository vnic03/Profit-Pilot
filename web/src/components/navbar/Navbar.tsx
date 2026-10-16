import React from "react";
import "./navbar.scss";

const Navbar: React.FC = () => (
    <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="Profit Pilot overview">
            <span className="brand-mark" aria-hidden="true">P</span>
            <span>
                <strong>Profit Pilot</strong>
                <small>Market research</small>
            </span>
        </a>

        <nav aria-label="Main navigation">
            <p className="nav-caption">Workspace</p>

            <a href="#analysis">Stock analysis</a>
            <a href="#movers">Market movers</a>
            <a href="#data-status">Data status</a>
            <a href="#news">News</a>
        </nav>

        <div className="sidebar-note">
            <span className="sidebar-badge">Daily data</span>
            <p>
                Research based on stored closing prices.
                Check each stock's latest price date.
            </p>

            <p className="sidebar-credit">
                Built by{" "}
                <a
                    href="https://github.com/vnic03"
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    Nico (@vnic03)
                </a>
            </p>
        </div>

    </aside>
);

export default Navbar;
