package com.flowora.erp.finance;

import com.flowora.erp.finance.FinanceDtos.JournalLineInput;
import com.flowora.erp.finance.FinanceDtos.ManualJournalCreate;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.AccountEntity;
import com.flowora.erp.masterdata.AccountRepository;
import com.flowora.erp.masterdata.AccountType;
import com.flowora.erp.sales.ReceivableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingServiceTest {
    @Test
    void incomeStatementSubtractsExpensesFromRevenue() {
        var entry = new JournalEntryEntity("org-a","J2","period",LocalDate.of(2026,10,1),"SALES_INVOICE","invoice","Profit","USD",new BigDecimal("200"),new BigDecimal("200"));
        var lines=List.of(new JournalLineEntity("org-a",entry.id(),1,"4000","Revenue",BigDecimal.ZERO,new BigDecimal("200"),"USD"),
                new JournalLineEntity("org-a",entry.id(),2,"5000","Expense",new BigDecimal("50"),BigDecimal.ZERO,"USD"));
        when(journalEntryRepository.inRange(anyString(),any(LocalDate.class),any(LocalDate.class))).thenReturn(List.of(entry));
        when(journalLineRepository.byJournalEntryIds(anyString(),any())).thenReturn(lines);
        when(accountRepository.findByOrganizationIdOrderByCode("org-a")).thenReturn(List.of(new AccountEntity("org-a","4000","Revenue",AccountType.REVENUE,null,true,true),new AccountEntity("org-a","5000","Expense",AccountType.EXPENSE,null,true,true)));
        assertThat(service.incomeStatement("org-a",LocalDate.of(2026,10,1),LocalDate.of(2026,10,1)).total()).isEqualByComparingTo("150");
    }
    @Test
    void trialBalanceUsesBaseAmountsAndIncludesReversedOriginals() {
        var original = new JournalEntryEntity("org-a", "J1", "period", LocalDate.of(2026,10,1), "REALIZED_EXCHANGE", "allocation", "FX", "EUR", BigDecimal.ONE, BigDecimal.ONE);
        org.springframework.test.util.ReflectionTestUtils.setField(original, "status", JournalEntryStatus.REVERSED);
        var line = new JournalLineEntity("org-a", original.id(),1,"1000","base",BigDecimal.ONE,BigDecimal.ZERO,"EUR");
        org.springframework.test.util.ReflectionTestUtils.setField(line,"baseDebit",new BigDecimal("7.1234"));
        when(journalEntryRepository.inRange(anyString(),any(LocalDate.class),any(LocalDate.class))).thenReturn(List.of(original));
        when(journalLineRepository.byJournalEntryIds(anyString(),any())).thenReturn(List.of(line));
        when(accountRepository.findByOrganizationIdOrderByCode("org-a")).thenReturn(List.of(new AccountEntity("org-a","1000","Cash",AccountType.ASSET,null,true,true)));
        assertThat(service.trialBalance("org-a",LocalDate.of(2026,10,1),LocalDate.of(2026,10,1)).totalDebit()).isEqualByComparingTo("7.1234");
    }
    @Mock private AccountingPeriodRepository periodRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private JournalLineRepository journalLineRepository;
    @Mock private PayableRepository payableRepository;
    @Mock private SupplierPaymentRepository supplierPaymentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ReceivableRepository receivableRepository;

    @InjectMocks
    private AccountingService service;

    @Test
    void postsBalancedManualJournalAndCreatesOpenPeriod() {
        stubPostingAccounts();
        when(journalEntryRepository.findByOrganizationIdAndSourceTypeAndSourceId(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(periodRepository.findByOrganizationIdAndYearAndMonth(anyString(), any(Integer.class), any(Integer.class))).thenReturn(Optional.empty());
        when(periodRepository.save(any(AccountingPeriodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalEntryRepository.save(any(JournalEntryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalLineRepository.save(any(JournalLineEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalLineRepository.findByOrganizationIdAndJournalEntryIdOrderByLineNo(anyString(), anyString())).thenReturn(List.of());

        var result = service.manual(actor(), new ManualJournalCreate(
                LocalDate.of(2026, 8, 7),
                "Manual cash reclassification",
                "USD",
                List.of(
                        new JournalLineInput("1000", "Cash", new BigDecimal("125.00"), BigDecimal.ZERO),
                        new JournalLineInput("1100", "Receivable", BigDecimal.ZERO, new BigDecimal("125.00"))
                )
        ));

        assertThat(result.status()).isEqualTo(JournalEntryStatus.POSTED);
        assertThat(result.totalDebit()).isEqualByComparingTo("125.00");
        assertThat(result.totalCredit()).isEqualByComparingTo("125.00");
        assertThat(result.currencyCode()).isEqualTo("USD");
    }

    @Test
    void rejectsUnbalancedManualJournal() {
        stubPostingAccounts();
        when(journalEntryRepository.findByOrganizationIdAndSourceTypeAndSourceId(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.manual(actor(), new ManualJournalCreate(
                LocalDate.of(2026, 8, 7),
                "Invalid journal",
                "USD",
                List.of(
                        new JournalLineInput("1000", "Cash", new BigDecimal("125.00"), BigDecimal.ZERO),
                        new JournalLineInput("1100", "Receivable", BigDecimal.ZERO, new BigDecimal("100.00"))
                )
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("balance");
    }

    private void stubPostingAccounts() {
        when(accountRepository.findByOrganizationIdAndCode(anyString(), anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(1);
            AccountType type = "1000".equals(code) ? AccountType.ASSET : AccountType.ASSET;
            return Optional.of(new AccountEntity("org-a", code, "Account " + code, type, null, true, true));
        });
    }

    private FloworaPrincipal actor() {
        return new FloworaPrincipal("user-1", "finance@example.com", "Finance", "org-a", "Demo", List.of("FINANCE"));
    }
}
