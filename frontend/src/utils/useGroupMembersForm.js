import { useEffect, useState } from 'react';
import { getGroupMembers } from '../api/groups';

/**
 * Loads a group's members for an expense-style form and preselects the current user (or the
 * first member) as the default payer. Shared by AddExpense and AddRecurringExpense.
 */
export function useGroupMembersForm(id) {
    const [members, setMembers] = useState([]);
    const [paidByUserId, setPaidByUserId] = useState('');
    const [loading, setLoading] = useState(true);
    const [errors, setErrors] = useState({});

    useEffect(() => {
        async function loadMembers() {
            const result = await getGroupMembers(id);
            if (result.error) {
                setErrors({ form: result.error });
            } else {
                setMembers(result.members);
                const self = result.members.find((member) => member.currentUser);
                setPaidByUserId(String((self ?? result.members[0])?.userId ?? ''));
            }
            setLoading(false);
        }

        loadMembers().catch(() => {
            setErrors({ form: 'Could not load group members.' });
            setLoading(false);
        });
    }, [id]);

    return { members, paidByUserId, setPaidByUserId, loading, errors, setErrors };
}
