package com.flowora.erp.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "flowora_accounting_period")
public class AccountingPeriodEntity extends AccountingEntity {
    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private AccountingPeriodStatus status;

    protected AccountingPeriodEntity() {
    }

    public AccountingPeriodEntity(String organizationId, int year, int month, LocalDate startDate, LocalDate endDate) {
        super(organizationId);
        this.year = year;
        this.month = month;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = AccountingPeriodStatus.OPEN;
    }

    public int year() { return year; }
    public int month() { return month; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public AccountingPeriodStatus status() { return status; }

    public void close() {
        if (status == AccountingPeriodStatus.CLOSED) throw new IllegalStateException("Accounting period is already closed");
        status = AccountingPeriodStatus.CLOSED;
    }
}
