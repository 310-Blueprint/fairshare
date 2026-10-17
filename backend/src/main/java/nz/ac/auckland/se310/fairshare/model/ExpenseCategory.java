package nz.ac.auckland.se310.fairshare.model;

/** #16 AC1: what an expense was for, so a member's spending can be broken down by category. */
public enum ExpenseCategory {
    FOOD_AND_DRINK("Food & drink"),
    GROCERIES("Groceries"),
    TRANSPORT("Transport"),
    ACCOMMODATION("Accommodation"),
    UTILITIES("Utilities"),
    ENTERTAINMENT("Entertainment"),
    SHOPPING("Shopping"),
    OTHER("Other");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
