import { apiFetch } from './config.js';
import { readError, requirePositiveInteger } from './groups.js';

// A 400 is either per-field validation errors, or one form-level { error } such as
// "The counterparty cannot be yourself", which no single field would display.
async function readBadRequest(response) {
    const body = await response.json();
    return body.error ? { error: body.error } : { errors: body };
}

// currency is optional: left empty, the server uses the creator's home currency.
export async function createIndividualDebt({ counterpartyIdentifier, amount, description, date, currency }) {
    const response = await apiFetch('/individual-debts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ counterpartyIdentifier, amount, description, date, currency: currency || null })
    });
    if (response.status === 400) {
        return readBadRequest(response);
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

// Users are sent by ID, since usernames are not unique.
export async function updateIndividualDebt(id, { payerUserId, debtorUserId, amount, description, date, currency }) {
    const debtId = requirePositiveInteger(id, 'Debt ID');
    const response = await apiFetch(`/individual-debts/${debtId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ payerUserId, debtorUserId, amount, description, date, currency })
    });
    if (response.status === 400) {
        return readBadRequest(response);
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
