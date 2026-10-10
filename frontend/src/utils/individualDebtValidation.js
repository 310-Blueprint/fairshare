// #2 AC9: amount must be a positive number, and the description and date are required.
// Shared by the add and edit pages.
export function validateIndividualDebtDetails({ amount, description, date }) {
    const errors = {};

    if (amount === '' || amount === null || amount === undefined) {
        errors.amount = 'Amount is required';
    } else if (Number.isNaN(Number(amount)) || Number(amount) <= 0) {
        errors.amount = 'Amount must be a positive number';
    }

    if (!description || description.trim() === '') {
        errors.description = 'Description is required';
    }

    if (!date) {
        errors.date = 'Date is required';
    }

    return errors;
}

// #2 AC9: a new entry also needs a counterparty.
export function validateIndividualDebtEntry({ counterpartyIdentifier, amount, description, date }) {
    const errors = {};

    if (!counterpartyIdentifier || counterpartyIdentifier.trim() === '') {
        errors.counterpartyIdentifier = 'Counterparty is required';
    }

    return { ...errors, ...validateIndividualDebtDetails({ amount, description, date }) };
}
