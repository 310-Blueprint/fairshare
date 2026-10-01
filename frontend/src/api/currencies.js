import { apiFetch } from './config.js';
import { readError } from './groups.js';

// AC3: the currencies an expense can be entered in, as { code, name } sorted by code.
export async function getCurrencies() {
    const response = await apiFetch('/currencies');
    if (!response.ok) {
        return { error: await readError(response, 'Could not load currencies.') };
    }
    return { currencies: await response.json() };
}
