import { useCallback, useEffect, useRef, useState } from 'react';
import { getGroupBalances, computeSettlement, markSettlementPaid, getGroupMembers } from '../api/groups';

export default function SettlementView({ groupId, baseCurrency, refreshVersion = 0 }) {
    const [balances, setBalances] = useState(null);
    const [members, setMembers] = useState([]);
    const [settlement, setSettlement] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [paying, setPaying] = useState(null);

    const requestVersion = useRef(0);

    // Refresh both balances and the persisted plan. Even an empty plan must reach the server
    // so obsolete pending payments are removed after the last expense is deleted.
    const loadBalances = useCallback(async () => {
        const request = ++requestVersion.current;
        setLoading(true);
        setError(null);
        setSettlement(null);
        try {
            const [b, memberResult] = await Promise.all([
                getGroupBalances(groupId),
                getGroupMembers(groupId)
            ]);
            if (request !== requestVersion.current) return;
            if (!Array.isArray(b) || memberResult.error) throw new Error('Could not load balances');
            const plan = await computeSettlement(groupId, b);
            if (request !== requestVersion.current) return;
            setBalances(b);
            setMembers(memberResult.members || []);
            setSettlement(plan || []);
        } catch {
            if (request === requestVersion.current) setError('Could not load settlement plan');
        } finally {
            if (request === requestVersion.current) setLoading(false);
        }
    }, [groupId]);

    useEffect(() => {
        void loadBalances();
        return () => { requestVersion.current++; };
    }, [loadBalances, refreshVersion]);

    // Marks a pending transfer as complete and then refreshes the balances so the UI reflects payment status.
    async function onMarkPaid(fromUserId, toUserId) {
        try {
            setPaying(`${fromUserId}-${toUserId}`);
            setError(null);
            await markSettlementPaid(groupId, fromUserId, toUserId);
            await loadBalances();
        } catch {
            setError('Failed to mark settlement as paid');
        } finally {
            setPaying(null);
        }
    }

    if (loading) return <p>Loading…</p>;
    if (error) return <p className="error">{error}</p>;

    if (!balances) return <p>Loading balances…</p>;

    // Resolve display names once so each settlement row can render a readable payer/recipient label.
    const memberLookup = new Map(members.map(member => [member.userId, member.username]));

    return (
        <div>
            <button className="button-small" onClick={() => void loadBalances()}>Generate settlement plan</button>
            {settlement && (
                <div>
                    {settlement.length === 0 ? (
                        <p className="empty">Everyone is settled up.</p>
                    ) : (
                        <ul className="settlement-list">
                            {settlement.map((s) => {
                                const fromUsername = memberLookup.get(s.fromUserId) || `User ${s.fromUserId}`;
                                const toUsername = memberLookup.get(s.toUserId) || `User ${s.toUserId}`;
                                const currentMember = members.find(m => m.currentUser);
                                const currentUserId = currentMember ? currentMember.userId : null;
                                const canMark = currentUserId !== null
                                    && (currentUserId === s.fromUserId || currentUserId === s.toUserId);
                                const settlementKey = `${s.fromUserId}-${s.toUserId}`;

                                return (
                                    <li key={settlementKey}>
                                        <span>{`${fromUsername} pays ${toUsername}: ${baseCurrency} ${Number.parseFloat(s.amount).toFixed(2)}`}</span>
                                        {canMark && (
                                            <button
                                                type="button"
                                                onClick={() => void onMarkPaid(s.fromUserId, s.toUserId)}
                                                className="button-small"
                                                disabled={paying === settlementKey}
                                            >
                                                {paying === settlementKey ? 'Marking paid…' : 'Mark as paid'}
                                            </button>
                                        )}
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}
