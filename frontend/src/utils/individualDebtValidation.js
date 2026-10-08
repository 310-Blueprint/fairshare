// #3 AC9: amount must be a positive number, the counterparty and description are required.
export function validateIndividualDebtEntry({ counterpartyIdentifier, amount, description, date }) {
    const errors = {};

    if (!counterpartyIdentifier || counterpartyIdentifier.trim() === '') {
        errors.counterpartyIdentifier = 'Counterparty is required';
    }

    if (amount === '' || amount === null || amount === undefined) {
        errors.amount = 'Amount is required';
    } else if (Number.isNaN(Number(amount)) || !(Number(amount) > 0)) {
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
