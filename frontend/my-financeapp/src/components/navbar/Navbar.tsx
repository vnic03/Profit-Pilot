import React from "react";
import { Link } from 'react-router-dom';
import './navbar.scss';

const Navbar: React.FC = () => {
    return (
        <nav className="navbar">
            <Link to="/">Home</Link>

            <div className="nav-links">
                <Link to="/dashboard">Dashboard</Link>
                <Link to="/portfolio">Portfolio</Link>
                <Link to="/tools">Tools</Link>
                <Link to="/settings">Settings</Link>
            </div>

            <div className="search-bar">
                <input type="text" placeholder="Suche..."/>
                <button type="submit">Search</button>
            </div>

        </nav>
    )
}

export default Navbar;
