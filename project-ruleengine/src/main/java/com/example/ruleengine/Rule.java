
package com.example.ruleengine;

public interface Rule<T> {
    boolean evaluate(T fact);
    void execute(T fact);
}
