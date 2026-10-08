import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createIndividualDebt } from '../api/individualDebts';
import { validateIndividualDebtEntry } from '../utils/individualDebtValidation';
import { today } from '../utils/dates';

function AddIndividualDebt() {
    const navigate = useNavigate();
    const [counterpartyIdentifier, setCounterpartyIdentifier] = useState('');
    const [amount, setAmount] = useState('');
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
            const result = await createIndividualDebt({ counterpartyIdentifier, amount, description, date });
            if (result.errors) {
                setErrors(result.errors);
                return;
            }
            if (result.error) {
                setErrors({ form: result.error });
                return;
            }
            navigate('/debts');
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
                    <div className="form-group">
                        <label htmlFor="counterparty">Who owes you?</label>
                        <input
                            id="counterparty"
                            value={counterpartyIdentifier}
                            onChange={(event) => setCounterpartyIdentifier(event.target.value)}
                            placeholder="name@example.com"
                        />
                        {errors.counterpartyIdentifier && <span className="error">{errors.counterpartyIdentifier}</span>}
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
                            placeholder="What was it for?"
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
                        {submitting ? 'Saving...' : 'Save'}
                    </button>
                </form>

                <Link to="/debts">Back to individual debts</Link>
            </div>
        </div>
    );
}

export default AddIndividualDebt;
