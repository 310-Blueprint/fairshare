// #16: "yyyy-MM" month keys, as the spending API sends and accepts them.

export function currentMonth() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function toDate(month) {
    const [year, monthNumber] = month.split('-').map(Number);
    return new Date(year, monthNumber - 1, 1);
}

// e.g. "October 2026"
export function formatMonth(month) {
    return toDate(month).toLocaleString('en-NZ', { month: 'long', year: 'numeric' });
}

// e.g. "Oct 2026"
export function formatShortMonth(month) {
    return toDate(month).toLocaleString('en-NZ', { month: 'short', year: 'numeric' });
}
