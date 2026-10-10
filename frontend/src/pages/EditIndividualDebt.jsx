import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getMyIndividualDebts, updateIndividualDebt } from '../api/individualDebts';
import { validateIndividualDebtDetails } from '../utils/individualDebtValidation';
import { today } from '../utils/dates';
import { useCurrencies } from '../utils/useCurrencies';
import { AmountField, CurrencyField, DescriptionField, FieldError } from '../components/ExpenseFormFields';
import { DateField } from '../components/IndividualDebtFormFields';

function EditIndividualDebt() {
    const { otherUserId, entryId } = useParams();
    const navigate = useNavigate();
    // The two people on the entry, as { userId, username }. The creator must stay one of them,
    // so editing can swap who owes whom but not move the entry to someone else.
    const [people, setPeople] = useState([]);
    const [payerUserId, setPayerUserId] = useState('');
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
                setPeople([
                    { userId: String(entry.payerUserId), username: entry.payerUsername },
                    { userId: String(entry.debtorUserId), username: entry.debtorUsername },
                ]);
                setPayerUserId(String(entry.payerUserId));
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

        const found = validateIndividualDebtDetails({ amount, description, date });
        if (Object.keys(found).length > 0) {
            setErrors(found);
            return;
        }

        setSubmitting(true);
        setErrors({});

        const debtorUserId = people.find((person) => person.userId !== payerUserId).userId;
        try {
            const result = await updateIndividualDebt(entryId, {
                payerUserId: Number(payerUserId),
                debtorUserId: Number(debtorUserId),
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
            void navigate(`/debts/${otherUserId}`);
        } catch {
            setErrors({ form: 'Could not save this entry. Please try again.' });
        } finally {
            setSubmitting(false);
        }
    }

    if (loading) return <div className="page"><p>Loading entry...</p></div>;

    if (errors.form && people.length === 0) {
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
                    <div className="form-group">
                        <label htmlFor="payer">Who is owed?</label>
                        <select id="payer" value={payerUserId} onChange={(event) => setPayerUserId(event.target.value)}>
                            {people.map((person) => (
                                <option key={person.userId} value={person.userId}>{person.username}</option>
                            ))}
                        </select>
                        <FieldError message={errors.payerUserId || errors.debtorUserId} />
                    </div>

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
