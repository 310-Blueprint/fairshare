import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { deleteIndividualDebt, getBalanceWithUser } from '../api/individualDebts';
import { money } from '../utils/formatExpenseAmount';
// The confirmation dialog's styles are shared, in index.css.
import './IndividualDebts.css';

function netLine(balance) {
    if (balance.settled) {
        return `You and ${balance.otherUsername} are settled up`;
    }
    const subject = balance.fromUserId === balance.otherUserId ? balance.otherUsername : 'You';
    const object = balance.toUserId === balance.otherUserId ? balance.otherUsername : 'you';
    return `${subject} owe${subject === 'You' ? '' : 's'} ${object} ${money(balance.currency, balance.amount)}`;
}

function entryLine(entry) {
    return `${entry.payerUsername} is owed ${money(entry.currency, entry.amount)} by ${entry.debtorUsername}`;
}

function IndividualDebtBalance() {
    const { otherUserId } = useParams();
    const [balance, setBalance] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [entryToDelete, setEntryToDelete] = useState(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState(null);

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

    async function handleDelete() {
        setDeleting(true);
        setDeleteError(null);
        try {
            const result = await deleteIndividualDebt(entryToDelete.id);
            if (result.error) {
                setDeleteError(result.error);
                return;
            }
            setEntryToDelete(null);
            const refreshed = await getBalanceWithUser(otherUserId);
            if (!refreshed.error) {
                setBalance(refreshed.balance);
            }
        } catch {
            setDeleteError('Could not delete this entry. Please try again.');
        } finally {
            setDeleting(false);
        }
    }

    function closeDeleteConfirmation() {
        setDeleteError(null);
        setEntryToDelete(null);
    }

    if (loading) return <div className="page"><p>Loading balance...</p></div>;

    if (error) {
        return (
            <div className="page">
                <div className="card">
                    <p className="error">{error}</p>
                    <p className="card-footer">
                        <Link to="/debts">Back to individual debts</Link>
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="page">
            <div className="card card-form">
                <h1>{balance.otherUsername}</h1>
                <p className="debt-net">{netLine(balance)}</p>

                <h2 className="debt-breakdown-heading">Breakdown</h2>
                {balance.entries.length === 0 ? (
                    <p className="empty">
                        No individual entries between you.
                        {/* Only an unsettled balance with no entries can have come from shared groups */}
                        {!balance.settled && ' This figure is from shared groups only.'}
                    </p>
                ) : (
                    <ul className="debt-list">
                        {balance.entries.map((entry) => (
                            <li key={entry.id} className="debt-entry">
                                <div className="debt-entry-text">
                                    <span className="debt-entry-line">{entryLine(entry)}</span>
                                    <span className="debt-entry-meta">{entry.description} · {entry.date}</span>
                                </div>
                                <div className="debt-entry-actions">
                                    {entry.canEdit && (
                                        <Link to={`/debts/${otherUserId}/entries/${entry.id}/edit`}>Edit</Link>
                                    )}
                                    {/* #2 AC7: only the person who is owed can write a debt off */}
                                    {entry.payerUserId !== Number(otherUserId) && (
                                        <button type="button" className="button-danger button-small" onClick={() => setEntryToDelete(entry)}>Delete</button>
                                    )}
                                </div>
                            </li>
                        ))}
                    </ul>
                )}

                <p className="card-footer">
                    <Link to="/debts">Back to individual debts</Link>
                </p>
            </div>

            {entryToDelete && (
                <div className="confirmation-backdrop">
                    <dialog
                        open
                        className="confirmation-dialog"
                        aria-labelledby="confirmation-title"
                    >
                        <h2 id="confirmation-title">Delete this entry?</h2>
                        <p>{entryLine(entryToDelete)} - {entryToDelete.description}. This cannot be undone.</p>
                        {deleteError && <p className="error" role="alert">{deleteError}</p>}
                        <div className="confirmation-actions">
                            <button type="button" className="button-secondary" disabled={deleting} onClick={closeDeleteConfirmation}>
                                Cancel
                            </button>
                            <button type="button" className="button-danger" disabled={deleting} onClick={() => void handleDelete()}>
                                {deleting ? 'Deleting...' : 'Delete'}
                            </button>
                        </div>
                    </dialog>
                </div>
            )}
        </div>
    );
}

export default IndividualDebtBalance;
