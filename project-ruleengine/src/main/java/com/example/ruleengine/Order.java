
package com.example.ruleengine;

public class Order {
    private double amount;
    private boolean freeShipping;
    private double discount = 0.0;

    public Order() {}
    public Order(double amount) { this.amount = amount; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public boolean isFreeShipping() { return freeShipping; }
    public void setFreeShipping(boolean freeShipping) { this.freeShipping = freeShipping; }
    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    @Override
    public String toString() {
        return "Order{amount=" + amount + ", freeShipping=" + freeShipping + ", discount=" + discount + "}";
    }
}
