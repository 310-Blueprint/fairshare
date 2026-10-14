import { useEffect, useState } from 'react';
import { getExpenseActivity } from '../api/expenses';

export default function ExpenseActivity({ groupId, refreshVersion = 0 }) {
    const [activity, setActivity] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        async function load() {
            try {
                const result = await getExpenseActivity(groupId);
                if (cancelled) return;
                if (result.error) setError(result.error);
                else setActivity(result.activity);
            } catch {
                if (!cancelled) setError('Could not load expense activity.');
            } finally {
                if (!cancelled) setLoading(false);
            }
        }
        void load();
        return () => { cancelled = true; };
    }, [groupId, refreshVersion]);

    return (
        <section aria-labelledby="expense-activity-heading">
            <h2 id="expense-activity-heading">Expense activity</h2>
            {loading ? <p>Loading expense activity...</p> : error ? (
                <p className="error" role="alert">{error}</p>
            ) : activity.length === 0 ? (
                <p className="empty">No expense edits or deletions yet.</p>
            ) : (
                <ul className="expense-activity-list">
                    {activity.map(event => (
                        <li key={event.id}>
                            <span>{event.actorUsername} {event.action === 'DELETED' ? 'deleted' : 'edited'} expense
                                {' "'}{event.expenseDescription}{'"'} (#{event.expenseId})</span>
                            <time dateTime={event.changedAt}>
                                {new Date(event.changedAt).toLocaleString()}
                            </time>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
