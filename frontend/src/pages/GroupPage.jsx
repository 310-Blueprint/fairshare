import {useEffect, useState} from 'react';
import {Link, useParams} from 'react-router-dom';
import {exportGroup, getGroup, getGroupMembers} from '../api/groups';
import {getExpenses, deleteExpense} from '../api/expenses';
import {getRecurringExpenses} from '../api/recurringExpenses';
import SettlementView from './SettlementView';
import ExpenseActivity from '../components/ExpenseActivity';
import {formatExpenseAmount} from '../utils/formatExpenseAmount';
import './GroupPage.css';

const FREQUENCY_LABELS = {
    WEEKLY: 'Weekly',
    FORTNIGHTLY: 'Fortnightly',
    MONTHLY: 'Monthly',
};

// Formats a numeric balance into the UI's currency display and keeps the sign readable.
function money(currency, value) {
    return `${currency} ${Math.abs(Number(value)).toFixed(2)}`;
}

// Converts the signed balance into a human-readable sentence about who owes or is owed.
function balanceLine(member, currency) {
    const balance = Number(member.netBalance);
    if (balance > 0) {
        return `${member.username} owes ${money(currency, balance)}`;
    } else if (balance < 0) {
        return `${member.username} is owed ${money(currency, balance)}`;
    }
    return `${member.username} is settled up`;
}

// #13 AC1, AC4: every recurring expense in the group, marked active or ended. A failure to load
// them is shown inline here rather than hiding the rest of the group page.
function RecurringExpenseList({recurringExpenses, error, currency}) {
    if (error) {
        return <span className="error">{error}</span>;
    }
    if (recurringExpenses.length === 0) {
        return <p className="empty">No recurring expenses yet.</p>;
    }
    return (
        <ul className="expense-list">
            {recurringExpenses.map((recurringExpense) => (
                <li key={recurringExpense.id} id={`recurring-expense-${recurringExpense.id}`}>
                    <span className="expense-description">{recurringExpense.description}</span>
                    <span className="expense-meta">
                        {FREQUENCY_LABELS[recurringExpense.frequency]} · paid by {recurringExpense.paidByUsername} · starts {recurringExpense.startDate}
                        {recurringExpense.endDate && ` · ends ${recurringExpense.endDate}`}
                        {' · '}{recurringExpense.active ? 'Active' : 'Ended'}
                    </span>
                    <span className="expense-amount">
                        {money(currency, recurringExpense.amount)}
                    </span>
                </li>
            ))}
        </ul>
    );
}

// #12: hands a downloaded blob to the browser as a file save.
function saveFile(blob, filename) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

function GroupPage() {
    const {id} = useParams();
    const [group, setGroup] = useState(null);
    const [expenses, setExpenses] = useState([]);
    const [members, setMembers] = useState([]);
    const [recurringExpenses, setRecurringExpenses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [recurringExpensesError, setRecurringExpensesError] = useState(null);
    const [notFound, setNotFound] = useState(false);
    const [exporting, setExporting] = useState(null);
    const [exportError, setExportError] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [expenseVersion, setExpenseVersion] = useState(0);

    // Fetch the group, expenses, and members whenever the route changes so the page stays in sync.
    useEffect(() => {
        async function load() {
            try {
                const result = await getGroup(id);
                if (result === null) {
                    setNotFound(true);        // AC8: not a member, or no such group
                    return;
                }
                setGroup(result);

                const [expenseResult, memberResult, recurringExpenseResult] = await Promise.all([
                    getExpenses(id),
                    getGroupMembers(id),
                    getRecurringExpenses(id),
                ]);

                if (expenseResult.error || memberResult.error) {
                    setError(expenseResult.error || memberResult.error);
                    return;
                }
                setExpenses(expenseResult.expenses);   // AC7
                setMembers(memberResult.members);      // AC1

                // A failure loading recurring expenses shouldn't hide the rest of the group page -
                // it's shown inline in that section instead (bug report from teammate review).
                if (recurringExpenseResult.error) {
                    setRecurringExpensesError(recurringExpenseResult.error);
                } else {
                    setRecurringExpenses(recurringExpenseResult.recurringExpenses);   // #13 AC1
                }
            } catch (err) {
                console.error('Failed to load group', err);
                setError('Could not load this group. Please try again.');
            } finally {
                setLoading(false);
            }
        }

        void load();   // errors are handled inside load()
    }, [id]);

    async function handleDelete(expense) {
        const currentUserId = members.find(member => member.currentUser)?.userId;
        if (deleting !== null || currentUserId == null || expense.createdByUserId !== currentUserId) return;
        if (!window.confirm(`Delete "${expense.description}"? This will remove the expense and update the group's balances.`)) {
            return;
        }
        setDeleting(expense.id);
        setDeleteError(null);
        try {
            const result = await deleteExpense(id, expense.id);
            if (result.error) {
                setDeleteError(result.error);
                return;
            }
            setExpenses(previous => previous.filter(item => item.id !== expense.id));
            setExpenseVersion(version => version + 1);
            const memberResult = await getGroupMembers(id);
            if (memberResult.error) {
                setDeleteError(`Expense deleted, but balances could not refresh: ${memberResult.error}`);
            } else {
                setMembers(memberResult.members);
            }
        } catch {
            setDeleteError('Could not complete deletion or refresh the group. Reload to check the latest history.');
        } finally {
            setDeleting(null);
        }
    }

    // #12: downloads the group's expenses as CSV or PDF.
    async function handleExport(format) {
        setExporting(format);
        setExportError(null);
        try {
            const result = await exportGroup(id, format);
            if (result.error) {
                setExportError(result.error);
                return;
            }
            saveFile(result.blob, result.filename);
        } catch (err) {
            console.error('Failed to export group', err);
            setExportError('Could not export this group. Please try again.');
        } finally {
            setExporting(null);
        }
    }

    if (loading) {
        return <div className="page"><p>Loading…</p></div>;
    }

    if (notFound) {
        return (
            <div className="page">
                <div className="card">
                    <h1>Group not found</h1>
                    <p className="empty">This group does not exist, or you are not a member of it.</p>
                    <Link to="/groups">Back to your groups</Link>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="page">
                <div className="card">
                    <span className="error">{error}</span>
                    <Link to="/groups">Back to your groups</Link>
                </div>
            </div>
        );
    }

    // A group is fully settled only when every member's net balance is zero.
    const currentUserId = members.find(member => member.currentUser)?.userId;
    const settled = members.every((member) => Number(member.netBalance) === 0);

    return (
        <div className="page">
            <div className="card card-wide group-card">
                <div className="card-header">
                    <div>
                        <h1>{group.name}</h1>
                        {group.description && <p className="group-description">{group.description}</p>}
                    </div>
                    <div className="group-actions">
                        <Link to="/groups">Back to your groups</Link>
                        <Link className="button button-secondary" to={`/groups/${id}/members`}>Manage members</Link>
                    </div>
                </div>

                {/* #83: sections sit in two columns on desktop widths */}
                <div className="group-sections">
                <div className="group-column">
                <section className="panel group-expenses">
                    <div className="section-header">
                        <h2>Expenses</h2>
                        <Link className="button button-small" to={`/groups/${id}/expenses/new`}>Add an expense</Link>
                    </div>

                    {deleteError && <p className="error" role="alert">{deleteError}</p>}

                    {/* AC7: every member sees the expense with amount, description, payer and date */}
                    {/* #8 AC7: Expenses can be edited */}
                    {expenses.length === 0 ? (
                        <p className="empty">No expenses yet.</p>
                    ) : (
                        <ul className="expense-list">
                            {expenses.map((expense) => (
                                <li key={expense.id}>
                                    <Link className="expense-edit"
                                          to={`/groups/${id}/expenses/${expense.id}/edit`}>Edit</Link>
                                    {currentUserId != null && expense.createdByUserId === currentUserId && (
                                        <div className="expense-actions">
                                            <Link className="action"
                                                  to={`/groups/${id}/expenses/${expense.id}/edit`}>Edit</Link>
                                            <button className="expense-delete-link" type="button" onClick={() => void handleDelete(expense)}
                                                    disabled={deleting !== null}
                                                    aria-label={`Delete ${expense.description}`}>
                                                {deleting === expense.id ? 'Deleting...' : 'Delete'}
                                            </button>
                                        </div>
                                    )}
                                    <span className="expense-description">{expense.description}</span>
                                    <span className="expense-meta">
                                        {expense.paidByUsername} paid on {expense.expenseDate}
                                        {/* #13 AC3: marks a generated entry and links back to its recurring expense */}
                                        {expense.recurringExpenseId && (
                                            <> · <a href={`#recurring-expense-${expense.recurringExpenseId}`}>Recurring</a></>
                                        )}
                                    </span>
                                    <span className="expense-amount">
                                        {formatExpenseAmount(expense, group.baseCurrency)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                <section className="panel group-recurring">
                    <div className="section-header">
                        <h2>Recurring expenses</h2>
                        <Link className="button button-small" to={`/groups/${id}/recurring-expenses/new`}>Add recurring expense</Link>
                    </div>
                <ExpenseActivity groupId={group.id} refreshVersion={expenseVersion} />

                <section>
                    <h2>Recurring expenses</h2>
                    <Link className="action" to={`/groups/${id}/recurring-expenses/new`}>Add recurring expense</Link>

                    <RecurringExpenseList recurringExpenses={recurringExpenses}
                                          error={recurringExpensesError}
                                          currency={group.baseCurrency}/>
                </section>

                </div>

                <div className="group-column">
                <section className="panel group-balances">
                    <h2>Balances</h2>
                    {/* AC1: each member's balance reflects the expenses recorded so far */}
                    {settled ? (
                        <p className="balance">
                            Everyone is settled up. Balance: {money(group.baseCurrency, 0)}
                        </p>
                    ) : (
                        <ul className="balance-list">
                            {members.map((member) => (
                                <li key={member.userId} className="balance">
                                    {balanceLine(member, group.baseCurrency)}
                                </li>
                            ))}
                        </ul>
                    )}
                    <Link className="section-link" to={`/groups/${id}/balance`}>View Detailed</Link>
                </section>

                <section className="panel group-settlement">
                    <h2>Settlement plan</h2>
                    <SettlementView groupId={group.id} baseCurrency={group.baseCurrency} refreshVersion={expenseVersion}/>
                </section>

                {/* #12: export sits with the other group-wide actions in the right-hand column */}
                <section className="panel group-export">
                    <div className="section-header">
                        <h2>Export</h2>
                        <div className="export-actions">
                            <button type="button" className="button-small" onClick={() => handleExport('csv')} disabled={exporting !== null}>
                                {exporting === 'csv' ? 'Exporting…' : 'Export CSV'}
                            </button>
                            <button type="button" className="button-small" onClick={() => handleExport('pdf')} disabled={exporting !== null}>
                                {exporting === 'pdf' ? 'Exporting…' : 'Export PDF'}
                            </button>
                        </div>
                    </div>
                    {exportError && <span className="error">{exportError}</span>}
                </section>
                </div>
                </div>
            </div>
        </div>
    );
}

export default GroupPage;
