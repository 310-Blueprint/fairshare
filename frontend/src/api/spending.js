import { apiFetch } from './config.js';
import { readError, requirePositiveInteger } from './groups.js';

const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

// #16: month is "yyyy-MM"; left out, the server summarises the current month.
export async function getSpendingSummary(userId, month) {
    const id = requirePositiveInteger(userId, 'User ID');
    const query = month && MONTH.test(month) ? `?month=${month}` : '';
    const response = await apiFetch(`/users/${id}/spending${query}`);
    if (!response.ok) {
        return { error: await readError(response, 'Could not load your spending.') };
    }
    return { summary: await response.json() };
}
