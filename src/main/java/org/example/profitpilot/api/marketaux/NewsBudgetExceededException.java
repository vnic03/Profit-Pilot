package org.example.profitpilot.api.marketaux;

public class NewsBudgetExceededException extends RuntimeException {

    public NewsBudgetExceededException() {
        super("The local news request budget is exhausted.");
    }
}
