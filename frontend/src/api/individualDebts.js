import { apiFetch } from './config.js';
import { readError, requirePositiveInteger } from './groups.js';

export async function createIndividualDebt({ counterpartyIdentifier, amount, description, date }) {
    const response = await apiFetch('/individual-debts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ counterpartyIdentifier, amount, description, date })
    });
    if (response.status === 400) {
        return { errors: await response.json() };
    }
    if (!response.ok) {
        return { error: await readError(response, 'Could not record this debt.') };
    }
    return { debt: await response.json() };
}

export async function getMyIndividualDebts() {
    const response = await apiFetch('/individual-debts');
    if (!response.ok) {
        return { error: await readError(response, 'Could not load your individual debts.') };
    }
    return { debts: await response.json() };
}

export async function updateIndividualDebt(id, { payerIdentifier, debtorIdentifier, amount, description, date }) {
    const debtId = requirePositiveInteger(id, 'Debt ID');
    const response = await apiFetch(`/individual-debts/${debtId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ payerIdentifier, debtorIdentifier, amount, description, date })
    });
    if (response.status === 400) {
        return { errors: await response.json() };
    }
    if (!response.ok) {
        return { error: await readError(response, 'Could not update this entry.') };
    }
    return { debt: await response.json() };
}

export async function deleteIndividualDebt(id) {
    const debtId = requirePositiveInteger(id, 'Debt ID');
    const response = await apiFetch(`/individual-debts/${debtId}`, { method: 'DELETE' });
    if (!response.ok) {
        return { error: await readError(response, 'Could not delete this entry.') };
    }
    return {};
}

export async function getBalancesOverview() {
    const response = await apiFetch('/individual-debts/balances');
    if (!response.ok) {
        return { error: await readError(response, 'Could not load your balances.') };
    }
    return { balances: await response.json() };
}

export async function getBalanceWithUser(otherUserId) {
    const userId = requirePositiveInteger(otherUserId, 'User ID');
    const response = await apiFetch(`/individual-debts/balances/${userId}`);
    if (!response.ok) {
        return { error: await readError(response, 'Could not load this balance.') };
    }
    return { balance: await response.json() };
}
