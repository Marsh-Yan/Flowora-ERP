package com.flowora.erp.project;

import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.CustomerRepository;
import com.flowora.erp.sales.SalesOrderRepository;
import com.flowora.erp.workflow.ActivityEventRepository;
import com.flowora.erp.workflow.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.flowora.erp.project.ProjectDtos.*;

@ExtendWith(MockitoExtension.class)
class ProjectOperationalCurrencyTest {
 @Mock ProjectRepository projects; @Mock MilestoneRepository milestones; @Mock ProjectTaskRepository tasks;
 @Mock TimesheetRepository timesheets; @Mock ProjectExpenseRepository expenses; @Mock ProjectBudgetRepository budgets;
 @Mock CustomerRepository customers; @Mock SalesOrderRepository salesOrders;
 @Mock ActivityEventRepository activities; @Mock AuditEventRepository audits;
 @InjectMocks ProjectService service;
 final String org="currency-org";
 final LocalDate date=LocalDate.of(2026,10,21);
 BigDecimal n(String value){return new BigDecimal(value);}
 ProjectEntity project(String currency){return new ProjectEntity(org,"PRJ-CURRENCY","Currency delivery",null,null,null,"manager",date,n("100"),n("50"),currency);}
 TimesheetEntity time(ProjectEntity project,String currency,String hours,String cost,String bill,boolean billable){return new TimesheetEntity(org,project.id(),null,"manager",date,n(hours),n(cost),n(bill),billable,currency,"Recorded labor");}
 ProjectExpenseEntity expense(ProjectEntity project,String currency,String amount,boolean billable){return new ProjectExpenseEntity(org,project.id(),null,"manager",date,"TRAVEL",n(amount),billable,currency,"Recorded expense");}
 ProjectResponse summary(ProjectEntity project,List<TimesheetEntity> labor,List<ProjectExpenseEntity> costs){
  when(projects.findByIdAndOrganizationId(project.id(),org)).thenReturn(Optional.of(project));
  when(timesheets.findByOrganizationIdAndProjectIdOrderByWorkDateDescCreatedAtDesc(org,project.id())).thenReturn(labor);
  when(expenses.findByOrganizationIdAndProjectIdOrderByExpenseDateDescCreatedAtDesc(org,project.id())).thenReturn(costs);
  return service.summary(org,project.id());
 }
 @Test void sameCurrencyRecordsKeepComparableLegacyScalarsAndHours(){
  var p=project("USD");var row=summary(p,List.of(time(p,"USD","2","10","20",true)),List.of(expense(p,"USD","5",true)));
  assertThat(row.actualCost()).isEqualByComparingTo("25");assertThat(row.billableAmount()).isEqualByComparingTo("45");assertThat(row.actualHours()).isEqualByComparingTo("2");assertThat(row.operationalAmounts()).containsExactly(new OperationalCurrencyAmount("USD",n("25"),n("45")));
 }
 @Test void mixedCurrenciesAreKeptSeparateAndNeverLabelledWithTheProjectCurrency(){
  var p=project("USD");var row=summary(p,List.of(time(p,"USD","2","10","20",true)),List.of(expense(p,"USD","5",true),expense(p,"CNY","100",true)));
  assertThat(row.actualCost()).isNull();assertThat(row.billableAmount()).isNull();assertThat(row.actualHours()).isEqualByComparingTo("2");assertThat(row.currencyCode()).isEqualTo("USD");assertThat(row.operationalAmounts()).containsExactly(new OperationalCurrencyAmount("CNY",n("100"),n("100")),new OperationalCurrencyAmount("USD",n("25"),n("45")));
 }
 @Test void aSingleForeignCurrencyIsNotRelabelledAsProjectCurrency(){
  var p=project("USD");var row=summary(p,List.of(),List.of(expense(p,"CNY","100",true)));
  assertThat(row.actualCost()).isNull();assertThat(row.billableAmount()).isNull();assertThat(row.operationalAmounts()).containsExactly(new OperationalCurrencyAmount("CNY",n("100"),n("100")));
 }
 @Test void emptyProjectHasZeroComparableScalarsAndNoInventedCurrencyRows(){
  var row=summary(project("EUR"),List.of(),List.of());assertThat(row.actualCost()).isEqualByComparingTo("0");assertThat(row.billableAmount()).isEqualByComparingTo("0");assertThat(row.actualHours()).isEqualByComparingTo("0");assertThat(row.operationalAmounts()).isEmpty();
 }
 @Test void nonBillableForeignCostsDoNotInvalidateComparableBillableAmount(){
  var p=project("USD");var row=summary(p,List.of(time(p,"USD","2","10","20",true)),List.of(expense(p,"CNY","100",false)));
  assertThat(row.actualCost()).isNull();assertThat(row.billableAmount()).isEqualByComparingTo("40");assertThat(row.operationalAmounts()).containsExactly(new OperationalCurrencyAmount("CNY",n("100"),BigDecimal.ZERO),new OperationalCurrencyAmount("USD",n("20"),n("40")));
 }
 @Test void groupedAmountsPreserveSourcePrecisionAndDoNotMutateRecords(){
  var p=project("USD");var a=time(p,"USD","1","0.1234","0.2345",true);var b=expense(p,"USD","0.0001",true);var row=summary(p,List.of(a),List.of(b));
  assertThat(row.actualCost()).isEqualByComparingTo("0.1235");assertThat(row.billableAmount()).isEqualByComparingTo("0.2346");assertThat(a.currencyCode()).isEqualTo("USD");assertThat(a.costAmount()).isEqualByComparingTo("0.1234");assertThat(b.amount()).isEqualByComparingTo("0.0001");verify(timesheets,never()).save(any());verify(expenses,never()).save(any());
 }
 @Test void listAndSummaryUseTheSameOrganizationQualifiedProjection(){
  var p=project("USD");var expected=summary(p,List.of(time(p,"CNY","2","10","20",true)),List.of());
  var page=PageRequest.of(0,20);when(projects.scopedSearch(eq(org),eq(""),isNull(),eq("ALL"),eq("manager"),isNull(),eq(page))).thenReturn(new PageImpl<>(List.of(p)));
  var actor=new FloworaPrincipal("manager","manager","Manager",org,"Currency organization",List.of("PROJECT_MANAGER"));
  assertThat(service.list(actor,"",null,page).content()).containsExactly(expected);verify(timesheets,times(2)).findByOrganizationIdAndProjectIdOrderByWorkDateDescCreatedAtDesc(org,p.id());verify(expenses,times(2)).findByOrganizationIdAndProjectIdOrderByExpenseDateDescCreatedAtDesc(org,p.id());
 }
 @Test void malformedHistoricalCurrencyFailsWithoutSilentlyConvertingOrRewritingIt(){
  var p=project("USD");var row=time(p,"???","1","10","20",true);
  assertThatThrownBy(()->summary(p,List.of(row),List.of())).isInstanceOf(IllegalStateException.class);assertThat(row.currencyCode()).isEqualTo("???");verify(timesheets,never()).save(any());
 }
}
