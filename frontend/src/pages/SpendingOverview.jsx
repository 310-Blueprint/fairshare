import { useEffect, useState } from 'react';
import { Link, useLoaderData } from 'react-router-dom';
import { getSpendingSummary } from '../api/spending';
import { money } from '../utils/formatExpenseAmount';
import { currentMonth, formatMonth, formatShortMonth } from '../utils/months';
import './SpendingOverview.css';

// Each bar's length is relative to the largest one in its chart.
function barWidth(amount, largest) {
    return largest > 0 ? `${(Number(amount) / largest) * 100}%` : '0%';
}

function BarRow({ label, amount, largest, children }) {
    return (
        <li>
            <span className="bar-label">{label}</span>
            <span className="bar-track">
                <span className="bar" style={{ width: barWidth(amount, largest) }} />
            </span>
            <span className="bar-amount">{children}</span>
        </li>
    );
}

function BreakdownChart({ title, lines, currency }) {
    const largest = Math.max(...lines.map((line) => Number(line.amount)));
    return (
        <section className="spending-section" aria-label={title}>
            <h2>{title}</h2>
            <ul className="bar-chart">
                {lines.map((line) => (
                    <BarRow key={line.key} label={line.label} amount={line.amount} largest={largest}>
                        {money(currency, line.amount)}
                    </BarRow>
                ))}
            </ul>
        </section>
    );
}

// AC2: an arrow and colour show whether spending went up or down from the month before.
function MonthChange({ change, currency }) {
    if (change === null || change === undefined) {
        return <span className="month-change">—</span>;
    }
    const value = Number(change);
    if (value === 0) {
        return <span className="month-change">No change</span>;
    }
    const up = value > 0;
    return (
        <span className={`month-change ${up ? 'change-up' : 'change-down'}`}>
            {up ? '▲' : '▼'} {up ? 'Up' : 'Down'} {money(currency, value)}
        </span>
    );
}

function MonthlyChart({ months, currency }) {
    const largest = Math.max(...months.map((entry) => Number(entry.total)));
    return (
        <section className="spending-section" aria-label="Month by month">
            <h2>Month by month</h2>
            <ul className="bar-chart">
                {months.map((entry) => (
                    <BarRow key={entry.month} label={formatShortMonth(entry.month)} amount={entry.total}
                            largest={largest}>
                        {money(currency, entry.total)}
                        <MonthChange change={entry.changeFromPreviousMonth} currency={currency} />
                    </BarRow>
                ))}
            </ul>
        </section>
    );
}

function SpendingOverview() {
    const user = useLoaderData();
    const [month, setMonth] = useState(currentMonth());
    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let active = true;
        setLoading(true);
        setError(null);
        getSpendingSummary(user.id, month)
            .then((result) => {
                if (!active) return;
                if (result.error) {
                    setError(result.error);
                    setSummary(null);
                } else {
                    setSummary(result.summary);
                }
            })
            .catch(() => {
                if (active) setError('Could not load your spending. Please try again.');
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [user.id, month]);

    // Clearing the month field leaves the last chosen month in place.
    function handleMonthChange(event) {
        if (event.target.value) {
            setMonth(event.target.value);
        }
    }

    const hasSpending = summary && Number(summary.total) > 0;
    // AC2: the last six months are charted once any of them has spending, so a month with
    // nothing before it is still compared against those empty months.
    const hasHistory = summary ? summary.months.some((entry) => Number(entry.total) > 0) : false;

    return (
        <div className="page">
            <div className="card spending-card">
                <h1>Your Spending</h1>
                <p className="subtitle">Your share of expenses in your groups</p>

                <div className="form-group">
                    <label htmlFor="month">Month</label>
                    <input id="month" type="month" value={month} max={currentMonth()} onChange={handleMonthChange} />
                </div>

                {loading && <p>Loading your spending...</p>}

                {!loading && error && <span className="error">{error}</span>}

                {!loading && summary && (
                    <>
                        {hasSpending ? (
                            <>
                                <div className="spending-total">
                                    <span>Total for {formatMonth(summary.month)}</span>
                                    <strong>{money(summary.currency, summary.total)}</strong>
                                </div>
                                <BreakdownChart title="By category" lines={summary.byCategory}
                                                currency={summary.currency} />
                                <BreakdownChart title="By group" lines={summary.byGroup}
                                                currency={summary.currency} />
                                <BreakdownChart title="By who paid" lines={summary.byPerson}
                                                currency={summary.currency} />
                            </>
                        ) : (
                            // AC3: a message instead of empty charts.
                            <p className="empty">No spending to display for {formatMonth(summary.month)}.</p>
                        )}

                        {hasHistory && (
                            <MonthlyChart months={summary.months} currency={summary.currency} />
                        )}
                    </>
                )}

                {/* #83: with no home page, link back to the groups overview this page is reached from */}
                <p className="card-footer">
                    <Link to="/groups">Back to your groups</Link>
                </p>
            </div>
        </div>
    );
}

export default SpendingOverview;
