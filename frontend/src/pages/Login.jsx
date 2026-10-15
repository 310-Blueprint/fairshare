import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { API_BASE } from '../api/config';
import FairShareLogo from '../resources/FairShareLogo.png';

function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');

    const response = await fetch(`${API_BASE}/users/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify({ email, password })
    });

    if (!response.ok) {
      const message = await response.text();
      setError(message || 'Invalid email or password');
      return;
    }

    navigate('/groups');
  }

  return (
    // #83: laid out like the home screen, with the logo and title above the card
    <div className="page hero hero-compact">
      <img src={FairShareLogo} alt="FairShare logo" className="hero-logo" />
      <h1 className="hero-title">Log In</h1>

      <div className="card">
        <p className="hero-tagline">Welcome back to FairShare</p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              placeholder="Enter your email"
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              placeholder="Enter your password"
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </div>

          {error && <div className="error">{error}</div>}

          <button type="submit">Log In</button>
        </form>

        <p className="card-footer">
          Need an account? <Link to="/register">Create profile</Link>
        </p>
      </div>
    </div>
  );
}

export default Login;
