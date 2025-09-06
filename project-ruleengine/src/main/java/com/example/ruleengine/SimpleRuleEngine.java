
package com.example.ruleengine;

import java.util.ArrayList;
import java.util.List;

public class SimpleRuleEngine<T> {
    private final List<Rule<T>> rules = new ArrayList<>();

    public void addRule(Rule<T> rule) { rules.add(rule); }

    public void run(T fact) {
        for (Rule<T> r : rules) {
            try {
                if (r.evaluate(fact)) r.execute(fact);
            } catch (Exception e) {
                // log and continue
                e.printStackTrace();
            }
        }
    }
}
