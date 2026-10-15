import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createIndividualDebt } from '../api/individualDebts';
import { validateIndividualDebtEntry } from '../utils/individualDebtValidation';
import { today } from '../utils/dates';
import { useCurrencies } from '../utils/useCurrencies';
import { AmountField, CurrencyField, DescriptionField, FieldError } from '../components/ExpenseFormFields';
import { DateField, IdentifierField } from '../components/IndividualDebtFormFields';

function AddIndividualDebt() {
    const navigate = useNavigate();
    const [counterpartyIdentifier, setCounterpartyIdentifier] = useState('');
    const [amount, setAmount] = useState('');
    const [currency, setCurrency] = useState(''); // empty means the user's home currency
    const { currencies, currenciesError } = useCurrencies();
    const [description, setDescription] = useState('');
    const [date, setDate] = useState(today());
    const [submitting, setSubmitting] = useState(false);
    const [errors, setErrors] = useState({});

    async function handleSubmit(event) {
        event.preventDefault();

        const found = validateIndividualDebtEntry({ counterpartyIdentifier, amount, description, date });
        if (Object.keys(found).length > 0) {
            setErrors(found);
            return;
        }

        setSubmitting(true);
        setErrors({});

        try {
            const result = await createIndividualDebt({ counterpartyIdentifier, amount, description, date, currency });
            if (result.errors) {
                setErrors(result.errors);
                return;
            }
            if (result.error) {
                setErrors({ form: result.error });
                return;
            }
            void navigate('/debts');
        } catch {
            setErrors({ form: 'Could not record this debt. Please try again.' });
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="page">
            <div className="card">
                <h1>Record a debt</h1>
                <p className="subtitle">They owe you - the selected user becomes the debtor</p>

                <form onSubmit={handleSubmit} noValidate>
                    <IdentifierField
                        id="counterparty"
                        label="Who owes you?"
                        value={counterpartyIdentifier}
                        onChange={setCounterpartyIdentifier}
                        error={errors.counterpartyIdentifier}
                        placeholder="name@example.com"
                    />

                    <AmountField value={amount} onChange={setAmount} error={errors.amount} />

                    <CurrencyField
                        value={currency}
                        currencies={currencies}
                        onChange={setCurrency}
                        error={errors.currency || currenciesError}
                        emptyLabel="My home currency"
                    />

                    <DescriptionField
                        value={description}
                        onChange={setDescription}
                        error={errors.description}
                        placeholder="What was it for?"
                    />

                    <DateField value={date} onChange={setDate} error={errors.date} max={today()} />

                    <FieldError message={errors.form} />

                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Saving...' : 'Save'}
                    </button>
                </form>

                <Link to="/debts">Back to individual debts</Link>
            </div>
        </div>
    );
}

export default AddIndividualDebt;
