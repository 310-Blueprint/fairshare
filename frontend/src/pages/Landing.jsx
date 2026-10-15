import {Link} from 'react-router-dom';
import {useEffect, useState} from 'react';
import './Landing.css';
import {getCurrentUser} from "../api/users.js";
import FairShareLogo from '../resources/FairShareLogo.png';

function Landing() {
    const [isLoggedIn, setIsLoggedIn] = useState(false);

    useEffect(() => {
        getCurrentUser()
            .then((user) => setIsLoggedIn(Boolean(user)))
            .catch(() =>  setIsLoggedIn(false))
    }, []);

    // #83: the profile page is reached from the header's profile button, so it isn't repeated here.
    return (
        <div className="page hero">
            <img src={FairShareLogo} alt="FairShare logo" className="hero-logo"/>
            <h1 className="hero-title">Welcome to Fair<span className="accent-text">Share</span></h1>

            <div className="card landing-card">
                <p className="hero-tagline">Easily split bills and manage group expenses</p>

                <div className="actions">
                    {isLoggedIn ? (
                        <>
                            <Link to="/groups/new" className="button">Create Group</Link>
                            <Link to="/groups" className="button">My Groups</Link>
                            <Link to="/debts" className="button">Individual Debts</Link>
                        </>
                    ) : (
                        <>
                            <Link to="/register" className="button">Create Profile</Link>
                            <Link to="/login" className="button">Log-in</Link>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}

export default Landing;
