package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.Condition;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.ConditionGroup;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.Logic;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.Operator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowConditionEvaluatorTest {
    private final WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator();

    @Test
    void evaluatesAmountBoundariesWithoutFloatingPointDrift() {
        ConditionGroup condition = new ConditionGroup(Logic.ALL, List.of(
                new Condition("amount", Operator.GTE, new BigDecimal("100.00")),
                new Condition("amount", Operator.LTE, new BigDecimal("200.00"))
        ));

        assertThat(evaluator.matches(condition, Map.of("amount", "100.000"))).isTrue();
        assertThat(evaluator.matches(condition, Map.of("amount", new BigDecimal("200.0000")))).isTrue();
        assertThat(evaluator.matches(condition, Map.of("amount", "200.01"))).isFalse();
    }

    @Test
    void supportsAnyInAndCaseInsensitiveEquality() {
        ConditionGroup condition = new ConditionGroup(Logic.ANY, List.of(
                new Condition("currency", Operator.IN, List.of("CNY", "USD")),
                new Condition("documentStatus", Operator.EQ, "urgent")
        ));

        assertThat(evaluator.matches(condition, Map.of("currency", "EUR", "documentStatus", "URGENT"))).isTrue();
        assertThat(evaluator.matches(condition, Map.of("currency", "USD", "documentStatus", "normal"))).isTrue();
    }

    @Test
    void missingFieldsNeverMatchRelationalConditions() {
        ConditionGroup condition = new ConditionGroup(Logic.ALL, List.of(
                new Condition("amount", Operator.GT, BigDecimal.ZERO)
        ));

        assertThat(evaluator.matches(condition, Map.of())).isFalse();
    }

    @Test
    void rejectsArbitraryFieldsAndNonCollectionInValues() {
        assertThatThrownBy(() -> evaluator.validate(new ConditionGroup(Logic.ALL, List.of(
                new Condition("java.lang.Runtime", Operator.EQ, "exec")
        )))).isInstanceOf(PlatformApiException.class)
                .extracting("code").isEqualTo("WORKFLOW_CONDITION_INVALID");

        assertThatThrownBy(() -> evaluator.validate(new ConditionGroup(Logic.ALL, List.of(
                new Condition("currency", Operator.IN, "USD")
        )))).isInstanceOf(PlatformApiException.class);
    }
}
