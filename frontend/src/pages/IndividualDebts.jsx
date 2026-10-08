import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getBalancesOverview } from '../api/individualDebts';

function balanceLine(balance) {
    if (balance.settled) {
        return `${balance.counterpartyUsername} is settled up`;
    }
    const direction = balance.fromUserId === balance.counterpartyUserId
        ? `${balance.counterpartyUsername} owes you`
        : `You owe ${balance.counterpartyUsername}`;
    return `${direction} ${Number(balance.amount).toFixed(2)}`;
}

function IndividualDebts() {
    const [balances, setBalances] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        async function load() {
            try {
                const result = await getBalancesOverview();
                if (result.error) {
                    setError(result.error);
                    return;
                }
                setBalances(result.balances);
            } catch {
                setError('Could not load your individual debts. Please try again.');
            } finally {
                setLoading(false);
            }
        }
        load();
    }, []);

    if (loading) return <div className="page"><p>Loading your balances...</p></div>;

    return (
        <div className="page">
            <div className="card">
                <h1>Individual Debts</h1>
                <p className="subtitle">What you and each person owe each other, combined</p>

                {error && <span className="error">{error}</span>}

                {!error && balances.length === 0 && (
                    <p className="empty">No individual debts yet.</p>
                )}

                <ul className="debt-list">
                    {balances.map((balance) => (
                        <li key={balance.counterpartyUserId}>
                            <Link to={`/debts/${balance.counterpartyUserId}`}>
                                {balanceLine(balance)}
                            </Link>
                        </li>
                    ))}
                </ul>

                <Link to="/debts/new" className="create-link">Record a debt</Link>
            </div>
        </div>
    );
}

export default IndividualDebts;
