import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getMyIndividualDebts, updateIndividualDebt } from '../api/individualDebts';
import { today } from '../utils/dates';
import { AmountField, DescriptionField, FieldError } from '../components/ExpenseFormFields';
import { DateField, IdentifierField } from '../components/IndividualDebtFormFields';

function EditIndividualDebt() {
    const { otherUserId, entryId } = useParams();
    const navigate = useNavigate();
    const [payerIdentifier, setPayerIdentifier] = useState('');
    const [debtorIdentifier, setDebtorIdentifier] = useState('');
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    const [date, setDate] = useState('');
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [errors, setErrors] = useState({});

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
                setPayerIdentifier(entry.payerUsername);
                setDebtorIdentifier(entry.debtorUsername);
                setAmount(String(entry.amount));
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
        setSubmitting(true);
        setErrors({});

        try {
            const result = await updateIndividualDebt(entryId, { payerIdentifier, debtorIdentifier, amount, description, date });
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

    if (errors.form && !payerIdentifier) {
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
                        id="payer"
                        label="Who is owed?"
                        value={payerIdentifier}
                        onChange={setPayerIdentifier}
                        error={errors.payerIdentifier}
                    />

                    <IdentifierField
                        id="debtor"
                        label="Who owes?"
                        value={debtorIdentifier}
                        onChange={setDebtorIdentifier}
                        error={errors.debtorIdentifier}
                    />

                    <AmountField value={amount} onChange={setAmount} error={errors.amount} />

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
