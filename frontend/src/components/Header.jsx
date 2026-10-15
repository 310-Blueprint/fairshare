import { Link, useLocation } from 'react-router-dom';
import FairShareLogo from '../resources/FairShareLogo.png';

// #83: the profile button from the redesign mockup, a person outline in a light circle.
function ProfileIcon() {
  return (
    <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20v-1.5A4.5 4.5 0 0 1 9.5 14h5a4.5 4.5 0 0 1 4.5 4.5V20" />
    </svg>
  );
}

function Header() {
  // Home and Log In show the large logo in the page itself, so the header only adds it elsewhere.
  const showsLargeLogo = ['/', '/login'].includes(useLocation().pathname);

  return (
    <header className="header">
      <div className="header-inner">
        <Link to="/profile" className="profile-button" aria-label="My profile">
          <ProfileIcon />
        </Link>
        {!showsLargeLogo && (
          <Link to="/" className="logo" aria-label="FairShare home">
            <img src={FairShareLogo} alt="FairShare logo" className="logo-image" />
          </Link>
        )}
      </div>
    </header>
  );
}

export default Header;
