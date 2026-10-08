import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { deleteIndividualDebt, getBalanceWithUser } from '../api/individualDebts';

function netLine(balance) {
    if (balance.settled) {
        return `You and ${balance.otherUsername} are settled up`;
    }
    const subject = balance.fromUserId === balance.otherUserId ? balance.otherUsername : 'You';
    const object = balance.toUserId === balance.otherUserId ? balance.otherUsername : 'you';
    return `${subject} owe${subject === 'You' ? '' : 's'} ${object} ${Number(balance.amount).toFixed(2)}`;
}

function entryLine(entry) {
    return `${entry.payerUsername} is owed ${Number(entry.amount).toFixed(2)} by ${entry.debtorUsername}`;
}

function IndividualDebtBalance() {
    const { otherUserId } = useParams();
    const [balance, setBalance] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        async function load() {
            try {
                const result = await getBalanceWithUser(otherUserId);
                if (result.error) {
                    setError(result.error);
                    return;
                }
                setBalance(result.balance);
            } catch {
                setError('Could not load this balance. Please try again.');
            } finally {
                setLoading(false);
            }
        }
        void load();
    }, [otherUserId]);

    async function handleDelete(entryId) {
        try {
            const result = await deleteIndividualDebt(entryId);
            if (result.error) {
                setError(result.error);
                return;
            }
            const refreshed = await getBalanceWithUser(otherUserId);
            if (!refreshed.error) {
                setBalance(refreshed.balance);
            }
        } catch {
            setError('Could not delete this entry. Please try again.');
        }
    }

    if (loading) return <div className="page"><p>Loading balance...</p></div>;

    if (error) {
        return (
            <div className="page">
                <div className="card">
                    <p className="error">{error}</p>
                    <Link to="/debts">Back to individual debts</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="page">
            <div className="card">
                <h1>{balance.otherUsername}</h1>
                <h2>{netLine(balance)}</h2>

                <h3>Breakdown</h3>
                {balance.entries.length === 0 ? (
                    <p className="empty">No individual entries between you - this figure is from shared groups only.</p>
                ) : (
                    <ul className="debt-list">
                        {balance.entries.map((entry) => (
                            <li key={entry.id}>
                                <span>{entryLine(entry)}</span>
                                <span> - {entry.description} ({entry.date})</span>
                                {entry.canEdit && (
                                    <span>
                                        {' '}
                                        <Link to={`/debts/${otherUserId}/entries/${entry.id}/edit`}>Edit</Link>
                                        {' '}
                                        <button type="button" onClick={() => void handleDelete(entry.id)}>Delete</button>
                                    </span>
                                )}
                            </li>
                        ))}
                    </ul>
                )}

                <Link to="/debts">Back to individual debts</Link>
            </div>
        </div>
    );
}

export default IndividualDebtBalance;
