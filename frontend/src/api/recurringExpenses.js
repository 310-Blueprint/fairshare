import { API_BASE } from './config.js';
import { readError, requirePositiveInteger } from './groups.js';

export async function getRecurringExpenses(id) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await fetch(`${API_BASE}/groups/${groupId}/recurring-expenses`, {
        credentials: 'include'
    });
    if (!response.ok) {
        return { error: await readError(response, 'Could not load recurring expenses.') };
    }
    return { recurringExpenses: await response.json() };
}

export async function createRecurringExpense(id, recurringExpense) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await fetch(`${API_BASE}/groups/${groupId}/recurring-expenses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(recurringExpense)
    });

    if (response.status === 400) {
        const body = await response.json();
        // AC5 comes back keyed by field. A rejection that belongs to no single field,
        // such as a payer who has left the group, comes back under "error".
        return { errors: body.error ? { form: body.error } : body };
    }
    if (!response.ok) {
        return { errors: { form: await readError(response, 'Could not add this recurring expense.') } };
    }
    return { recurringExpense: await response.json() };
}

export async function getRecurringExpense(id, recurringExpenseId) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await fetch(
        `${API_BASE}/groups/${groupId}/recurring-expenses/${recurringExpenseId}`,
        { credentials: 'include' }
    );

    if (!response.ok) {
        return { error: await readError(response, 'Could not load recurring expense.') };
    }

    return { recurringExpense: await response.json() };
}
