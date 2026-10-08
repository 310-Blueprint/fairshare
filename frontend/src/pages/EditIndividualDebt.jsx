import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getMyIndividualDebts, updateIndividualDebt } from '../api/individualDebts';
import { today } from '../utils/dates';

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
                if (!entry || !entry.canEdit) {
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
        load();
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
            navigate(`/debts/${otherUserId}`);
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
                    <div className="form-group">
                        <label htmlFor="payer">Who is owed?</label>
                        <input
                            id="payer"
                            value={payerIdentifier}
                            onChange={(event) => setPayerIdentifier(event.target.value)}
                        />
                        {errors.payerIdentifier && <span className="error">{errors.payerIdentifier}</span>}
                    </div>

                    <div className="form-group">
                        <label htmlFor="debtor">Who owes?</label>
                        <input
                            id="debtor"
                            value={debtorIdentifier}
                            onChange={(event) => setDebtorIdentifier(event.target.value)}
                        />
                        {errors.debtorIdentifier && <span className="error">{errors.debtorIdentifier}</span>}
                    </div>

                    <div className="form-group">
                        <label htmlFor="amount">Amount</label>
                        <input
                            id="amount"
                            type="number"
                            step="0.01"
                            value={amount}
                            onChange={(event) => setAmount(event.target.value)}
                        />
                        {errors.amount && <span className="error">{errors.amount}</span>}
                    </div>

                    <div className="form-group">
                        <label htmlFor="description">Description</label>
                        <input
                            id="description"
                            value={description}
                            onChange={(event) => setDescription(event.target.value)}
                        />
                        {errors.description && <span className="error">{errors.description}</span>}
                    </div>

                    <div className="form-group">
                        <label htmlFor="date">Date</label>
                        <input
                            id="date"
                            type="date"
                            value={date}
                            max={today()}
                            onChange={(event) => setDate(event.target.value)}
                        />
                        {errors.date && <span className="error">{errors.date}</span>}
                    </div>

                    {errors.form && <span className="error">{errors.form}</span>}

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
