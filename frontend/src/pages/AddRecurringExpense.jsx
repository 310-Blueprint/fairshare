import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { createRecurringExpense } from '../api/recurringExpenses';
import RecurringExpenseForm from '../components/RecurringExpenseForm';
import { today } from '../utils/dates';
import { validateSharedExpenseFields } from '../utils/expenseValidation';
import { useGroupMembersForm } from '../utils/useGroupMembersForm';
import './AddExpense.css';

// Generation splits into whole cents, so a fractional cent (e.g. 10.005) can't be split exactly.
function hasMoreThanTwoDecimalPlaces(amount) {
    const decimalIndex = amount.indexOf('.');
    return decimalIndex !== -1 && amount.length - decimalIndex - 1 > 2;
}

function validate({ amount, description, paidByUserId, frequency, startDate, endDate, participantUserIds }) {
    const errors = validateSharedExpenseFields({ amount, description, paidByUserId, participantUserIds });

    if (!errors.amount && hasMoreThanTwoDecimalPlaces(amount.trim())) {
        errors.amount = 'Amounts should only have up to 2 decimal places.';
    }

    if (frequency === '') {
        errors.frequency = 'Frequency is required';
    }

    if (startDate === '') {
        errors.startDate = 'Start date is required';
    }

    if (endDate && endDate < startDate) {
        errors.endDate = 'End date cannot be before the start date';
    }

    return errors;
}

function AddRecurringExpense() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { members, paidByUserId, setPaidByUserId, loading, errors, setErrors } = useGroupMembersForm(id);
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    const [frequency, setFrequency] = useState('');
    const [startDate, setStartDate] = useState(today());
    const [endDate, setEndDate] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [participantUserIds, setParticipantUserIds] = useState([]);

    async function handleSubmit(event) {
        event.preventDefault();

        const found = validate({ amount, description, paidByUserId, frequency, startDate, endDate, participantUserIds });
        if (Object.keys(found).length > 0) {
            setErrors(found);
            return;
        }

        setSubmitting(true);
        setErrors({});

        try {
            const result = await createRecurringExpense(id, {
                amount,
                description,
                paidByUserId: Number(paidByUserId),
                frequency,
                startDate,
                endDate: endDate || null,
                participantUserIds: participantUserIds.map(Number)
            });

            if (result.errors) {
                setErrors(result.errors);
                return;
            }

            navigate(`/groups/${id}`);
        } catch {
            setErrors({ form: 'Could not add this recurring expense. Please try again.' });
        } finally {
            setSubmitting(false);
        }
    }

    if (loading) {
        return <div className="page"><p>Loading…</p></div>;
    }

    return (
        <div className="page">
            <div className="card">
                <h1>Add a recurring expense</h1>
                <p className="subtitle">Automatically split on a schedule</p>

                <RecurringExpenseForm
                    amount={amount}
                    description={description}
                    paidByUserId={paidByUserId}
                    frequency={frequency}
                    startDate={startDate}
                    endDate={endDate}
                    participantUserIds={participantUserIds}
                    members={members}
                    errors={errors}
                    submitting={submitting}
                    onAmountChange={setAmount}
                    onDescriptionChange={setDescription}
                    onPaidByUserIdChange={setPaidByUserId}
                    onFrequencyChange={setFrequency}
                    onStartDateChange={setStartDate}
                    onEndDateChange={setEndDate}
                    onParticipantUserIdsChange={setParticipantUserIds}
                    onSubmit={handleSubmit}
                />

                <Link to={`/groups/${id}`}>Back to the group</Link>
            </div>
        </div>
    );
}

export default AddRecurringExpense;
