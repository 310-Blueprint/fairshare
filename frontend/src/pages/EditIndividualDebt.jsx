import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getMyIndividualDebts, updateIndividualDebt } from '../api/individualDebts';
import { validateIndividualDebtEntry } from '../utils/individualDebtValidation';
import { today } from '../utils/dates';
import { useCurrencies } from '../utils/useCurrencies';
import { AmountField, CurrencyField, DescriptionField, FieldError } from '../components/ExpenseFormFields';
import { DateField, IdentifierField } from '../components/IndividualDebtFormFields';

function EditIndividualDebt() {
    const { otherUserId, entryId } = useParams();
    const navigate = useNavigate();
    // #2 AC8: the creator can correct who owes them, but always stays the person owed. The original
    // username is kept so an unchanged field is not looked up again (usernames are not unique).
    const [originalCounterparty, setOriginalCounterparty] = useState(null);
    const [counterpartyIdentifier, setCounterpartyIdentifier] = useState('');
    const [amount, setAmount] = useState('');
    const [currency, setCurrency] = useState('');
    const [description, setDescription] = useState('');
    const [date, setDate] = useState('');
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [errors, setErrors] = useState({});
    const { currencies, currenciesError } = useCurrencies();

    useEffect(() => {
        async function load() {
            try {
                const result = await getMyIndividualDebts();
                if (result.error) {
                    setErrors({ form: result.error });
                    return;
                }
                const entry = result.debts.find((candidate) => String(candidate.id) === entryId);
                if (!entry?.canEdit) {
                    setErrors({ form: 'This entry could not be found, or you did not create it.' });
                    return;
                }
                setOriginalCounterparty(entry.debtorUsername);
                setCounterpartyIdentifier(entry.debtorUsername);
                setAmount(String(entry.amount));
                setCurrency(entry.currency);
                setDescription(entry.description);
                setDate(entry.date);
            } catch {
                setErrors({ form: 'Could not load this entry. Please try again.' });
            } finally {
                setLoading(false);
            }
        }
        void load();
    }, [entryId]);

    async function handleSubmit(event) {
        event.preventDefault();

        const found = validateIndividualDebtEntry({ counterpartyIdentifier, amount, description, date });
        if (Object.keys(found).length > 0) {
            setErrors(found);
            return;
        }

        setSubmitting(true);
        setErrors({});

        const counterpartyChanged = counterpartyIdentifier.trim() !== originalCounterparty;
        try {
            const result = await updateIndividualDebt(entryId, {
                counterpartyIdentifier: counterpartyChanged ? counterpartyIdentifier : null,
                amount,
                description,
                date,
                currency,
            });
            if (result.errors) {
                setErrors(result.errors);
                return;
            }
            if (result.error) {
                setErrors({ form: result.error });
                return;
            }
            // A new debtor moves the entry off this balance page, so go back to the overview instead.
            void navigate(counterpartyChanged ? '/debts' : `/debts/${otherUserId}`);
        } catch {
            setErrors({ form: 'Could not save this entry. Please try again.' });
        } finally {
            setSubmitting(false);
        }
    }

    if (loading) return <div className="page"><p>Loading entry...</p></div>;

    if (errors.form && originalCounterparty === null) {
        return (
            <div className="page">
                <div className="card">
                    <p className="error">{errors.form}</p>
                    <Link to={`/debts/${otherUserId}`}>Back to balance</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="page">
            <div className="card">
                <h1>Edit entry</h1>

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
                    />

                    <DescriptionField value={description} onChange={setDescription} error={errors.description} />

                    <DateField value={date} onChange={setDate} error={errors.date} max={today()} />

                    <FieldError message={errors.form} />

                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Saving...' : 'Save changes'}
                    </button>
                </form>

                <Link to={`/debts/${otherUserId}`}>Back to balance</Link>
            </div>
        </div>
    );
}

export default EditIndividualDebt;
