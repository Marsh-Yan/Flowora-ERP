package com.flowora.erp.project;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.project.v2.ProjectFinanceDtos.MilestoneApprovalAction;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class MilestoneTermsTest {
 @Test void legacyCreationRemainsNonBillableWithoutApprovalOrQuota(){var value=new MilestoneEntity("org","project","Delivery",1,null);assertThat(value.billable()).isFalse();assertThat(value.billingAmount()).isEqualByComparingTo("0");assertThat(value.approvalStatus()).isEqualTo("NOT_REQUIRED");assertThat(value.status()).isEqualTo(MilestoneStatus.PLANNED);}
 @Test void billableTermsRetainFourDecimalsAndAwaitSeparateApproval(){var value=new MilestoneEntity("org","project","Delivery",1,null,true,new BigDecimal("100.1234"));assertThat(value.billingAmount()).isEqualByComparingTo("100.1234");assertThat(value.approvalStatus()).isEqualTo("DRAFT");assertThat(value.status()).isEqualTo(MilestoneStatus.PLANNED);}
 @Test void invalidOrContradictoryAmountsCannotBecomeMilestoneTerms(){for(String amount:new String[]{"0","-1","0.00001","1000000000000000"})assertThatThrownBy(()->new MilestoneEntity("org","project","Delivery",1,null,true,new BigDecimal(amount))).isInstanceOf(PlatformApiException.class);assertThatThrownBy(()->new MilestoneEntity("org","project","Delivery",1,null,false,BigDecimal.ONE)).isInstanceOf(PlatformApiException.class);}
 @Test void statusAndApprovalRequireAnExplicitVersionDecisionAndReviewReason(){try(var factory=Validation.buildDefaultValidatorFactory()){var v=factory.getValidator();assertThat(v.validate(new ProjectDtos.MilestoneStatusChange(MilestoneStatus.COMPLETED,null))).isNotEmpty();assertThat(v.validate(new MilestoneApprovalAction(null,"Review",0L))).isNotEmpty();assertThat(v.validate(new MilestoneApprovalAction(true," ",0L))).isNotEmpty();assertThat(v.validate(new MilestoneApprovalAction(true,"Review",-1L))).isNotEmpty();}}
}
