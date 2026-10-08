import { apiFetch } from './config.js';
import { readError, requirePositiveInteger } from './groups.js';

export async function extractReceipt(id, file) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiFetch(`/groups/${groupId}/receipts/extract`, {
        method: 'POST',
        body: formData,
    });

    if (!response.ok) {
        return {
            error: await readError(
                response,
                "We couldn't read this receipt. Enter the expense manually or try again."
            )
        };
    }
    return { receipt: await response.json() };
}
