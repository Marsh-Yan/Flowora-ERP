package com.flowora.erp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "flowora_sales_quote_line")
public class SalesQuoteLineEntity extends SalesEntity {
    @Column(name = "quote_id", length = 36, nullable = false)
    private String quoteId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_rate", precision = 9, scale = 4, nullable = false)
    private BigDecimal discountRate;

    @Column(name = "tax_rate", precision = 9, scale = 4, nullable = false)
    private BigDecimal taxRate;

    protected SalesQuoteLineEntity() {
    }

    public SalesQuoteLineEntity(String organizationId, String quoteId, String itemId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal taxRate) {
        super(organizationId);
        this.quoteId = quoteId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountRate = discountRate;
        this.taxRate = taxRate;
    }

    public String quoteId() { return quoteId; }
    public String itemId() { return itemId; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal unitPrice() { return unitPrice; }
    public BigDecimal discountRate() { return discountRate; }
    public BigDecimal taxRate() { return taxRate; }
    public BigDecimal lineTotal() { return total(quantity, unitPrice, discountRate, taxRate); }

    static BigDecimal total(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal taxRate) {
        BigDecimal gross = quantity.multiply(unitPrice);
        BigDecimal discounted = gross.subtract(gross.multiply(discountRate).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
        BigDecimal tax = discounted.multiply(taxRate).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        return discounted.add(tax).setScale(4, RoundingMode.HALF_UP);
    }
}
