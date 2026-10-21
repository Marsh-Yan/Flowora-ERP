package com.flowora.erp.project;

import com.flowora.erp.finance.v2.FinanceLedgerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.flowora.erp.finance.v2.FinanceLedgerService.conflict;
import static com.flowora.erp.finance.v2.FinanceLedgerService.notFound;

/** Shared close policy. Writers take the finance lock before the project lock. */
@Service
public class ProjectLifecycleService {
    private final JdbcTemplate jdbc;
    private final FinanceLedgerService ledger;
    public ProjectLifecycleService(JdbcTemplate jdbc, FinanceLedgerService ledger) { this.jdbc=jdbc;this.ledger=ledger; }

    public void lockOrganization(String org) { ledger.lockOrganizationFinance(org); }

    public void requireOpen(String org,String id) {
        lockOrganization(org);
        var state=header(org,id,true);
        if (terminal(state.status())) throw conflict("PROJECT_CLOSED",Map.of("status",state.status()));
    }

    @Transactional
    public void changeStatus(String org,String id,ProjectStatus next) {
        lockOrganization(org);
        var state=header(org,id,true);
        ProjectEntity.requireTransition(state.status(),next);
        // Re-check even a repeated terminal request; old inconsistent closures are not silently certified.
        if (next!=null && terminal(next)) {
            var readiness=assess(org,id,state,true);
            if (!readiness.eligible()) throw conflict("PROJECT_CLOSE_BLOCKED",Map.of("blockers",readiness.blockers()));
        }
        if(next!=null && next!=state.status())jdbc.update("UPDATE flowora_project SET status=?,version_no=version_no+1 WHERE organization_id=? AND id=?",next.name(),org,id);
    }

    public Readiness readiness(String org,String id) {
        var state=header(org,id,false);
        return assess(org,id,state,false);
    }

    private Header header(String org,String id,boolean locked) {
        var rows=jdbc.query("SELECT status,currency_code,billing_mode,contract_amount FROM flowora_project WHERE organization_id=? AND id=?"+(locked?" FOR UPDATE":""),
                (rs,n)->new Header(ProjectStatus.valueOf(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getBigDecimal(4)),org,id);
        if(rows.isEmpty())throw notFound("project",id);
        return rows.getFirst();
    }

    private Readiness assess(String org,String id,Header header,boolean locked) {
        String currency=header.currency();
        String suffix=locked?" FOR UPDATE":"";
        // Locking reads are current reads even if this transaction already has an older MySQL snapshot.
        int open=jdbc.query("SELECT status FROM flowora_project_task WHERE organization_id=? AND project_id=? ORDER BY id"+suffix,
                (rs,n)->rs.getString(1),org,id).stream().filter(s->!"DONE".equals(s)).toList().size();
        int pending=0;
        var approvedSources=new ArrayList<ApprovedSource>();
        for(String type:List.of("TIMESHEET","EXPENSE")) {
            String table="TIMESHEET".equals(type)?"flowora_timesheet":"flowora_project_expense";
            var sources=jdbc.query("SELECT id,approval_status,billable,billable_amount,currency_code FROM "+table+" WHERE organization_id=? AND project_id=? ORDER BY id"+suffix,
                    (rs,n)->new Approval(rs.getString(1),rs.getString(2),rs.getBoolean(3),rs.getBigDecimal(4),rs.getString(5)),org,id);
            for(var source:sources) {
                if(!List.of("APPROVED","REJECTED").contains(source.status()))pending++;
                if("APPROVED".equals(source.status()) && source.billable() && source.amount().signum()>0)approvedSources.add(new ApprovedSource(type,source.id(),source.amount(),source.currency()));
            }
        }
        var milestoneApprovals=jdbc.query("SELECT id,approval_status,billing_amount FROM flowora_project_milestone WHERE organization_id=? AND project_id=? AND status='COMPLETED' AND billable=TRUE AND billing_amount>0 ORDER BY id"+suffix,
                (rs,n)->new Approval(rs.getString(1),rs.getString(2),true,rs.getBigDecimal(3),currency),org,id);
        for(var source:milestoneApprovals) {
            if(!List.of("APPROVED","REJECTED").contains(source.status()))pending++;
            if("APPROVED".equals(source.status()))approvedSources.add(new ApprovedSource("MILESTONE",source.id(),source.amount(),source.currency()));
        }
        int invoices=jdbc.query("""
                SELECT i.status,i.settlement_status FROM flowora_finance_invoice i
                WHERE i.organization_id=? AND (i.project_id=? OR EXISTS(
                  SELECT 1 FROM flowora_finance_invoice_line l WHERE l.organization_id=i.organization_id AND l.invoice_id=i.id AND l.project_id=?))
                ORDER BY i.id
                """+suffix,(rs,n)->List.of(rs.getString(1),rs.getString(2)),org,id,id).stream()
                .filter(s->"DRAFT".equals(s.get(0)) || ("POSTED".equals(s.get(0)) && !"PAID".equals(s.get(1)))).toList().size();
        var bases=jdbc.query("SELECT id,available_amount,billed_amount,currency_code,status,approved_at,basis_type,source_id FROM flowora_project_billing_basis WHERE organization_id=? AND project_id=? ORDER BY id"+suffix,
                (rs,n)->new Basis(rs.getString(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6)!=null,rs.getString(7),rs.getString(8)),org,id);
        var reservations=new java.util.HashMap<String,BigDecimal>();
        jdbc.query("""
                SELECT s.source_line_id,s.amount FROM flowora_finance_invoice_source s
                JOIN flowora_finance_invoice_line l ON l.id=s.invoice_line_id AND l.organization_id=s.organization_id
                JOIN flowora_finance_invoice i ON i.id=l.invoice_id AND i.organization_id=l.organization_id
                WHERE s.organization_id=? AND s.source_type='PROJECT_BILLING_BASIS' AND s.source_id=?
                  AND i.document_type='SALES_INVOICE' AND i.status IN ('DRAFT','POSTED') ORDER BY s.id
                """+suffix,(rs,n)->{reservations.merge(rs.getString(1),rs.getBigDecimal(2),BigDecimal::add);return rs.getString(1);},org,id);
        int unbilled=0,invalid=0;
        for(var b:bases) {
            if(b.available().compareTo(b.billed())>0)unbilled++;
            String expected=b.billed().signum()==0?"AVAILABLE":b.billed().compareTo(b.available())==0?"BILLED":"PARTIAL";
            if(b.billed().compareTo(reservations.getOrDefault(b.id(),BigDecimal.ZERO))!=0 || b.available().signum()<=0 || b.billed().signum()<0 || b.billed().compareTo(b.available())>0 || !currency.equals(b.currency()) || !expected.equals(b.status()) || !b.approved())invalid++;
        }
        if(reservations.keySet().stream().anyMatch(key->bases.stream().noneMatch(b->b.id().equals(key))))invalid++;
        for(var source:approvedSources)if(bases.stream().noneMatch(b->source.type().equals(b.type()) && source.id().equals(b.sourceId()) && source.currency().equals(b.currency()) && source.amount().compareTo(b.available())==0))invalid++;
        if("FIXED_PRICE".equals(header.billingMode()) && header.contractAmount().signum()>0 && bases.stream().noneMatch(b->"FIXED_PRICE".equals(b.type()) && id.equals(b.sourceId())))invalid++;
        var blockers=new ArrayList<String>();
        if(open>0)blockers.add("OPEN_TASKS");if(pending>0)blockers.add("PENDING_APPROVALS");
        if(invoices>0)blockers.add("UNSETTLED_INVOICES");if(unbilled>0)blockers.add("UNBILLED_BASES");if(invalid>0)blockers.add("INVALID_BILLING_BASES");
        return new Readiness(open,pending,invoices,unbilled,invalid,List.copyOf(blockers));
    }
    public static boolean terminal(ProjectStatus state) { return state==ProjectStatus.COMPLETED || state==ProjectStatus.ARCHIVED; }
    private record Header(ProjectStatus status,String currency,String billingMode,BigDecimal contractAmount) { }
    private record Approval(String id,String status,boolean billable,BigDecimal amount,String currency) { }
    private record ApprovedSource(String type,String id,BigDecimal amount,String currency) { }
    private record Basis(String id,BigDecimal available,BigDecimal billed,String currency,String status,boolean approved,String type,String sourceId) { }
    public record Readiness(int openTasks,int pendingApprovals,int unsettledInvoices,int unbilledBases,int invalidBillingBases,List<String> blockers) {
        public boolean eligible(){return blockers.isEmpty();}
    }
}
