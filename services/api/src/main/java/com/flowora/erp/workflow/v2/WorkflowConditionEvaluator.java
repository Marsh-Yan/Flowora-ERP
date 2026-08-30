package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.Condition;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.ConditionGroup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowConditionEvaluator {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "amount", "baseAmount", "currency", "departmentId", "customerRisk",
            "supplierRisk", "discount", "resourceType", "ownerUserId", "documentStatus"
    );

    public void validate(ConditionGroup group) {
        if (group == null || group.conditions() == null) invalid("condition");
        for (Condition condition : group.conditions()) {
            if (!ALLOWED_FIELDS.contains(condition.field())) invalid(condition.field());
            if (condition.operator() == WorkflowV2Dtos.Operator.IN
                    && !(condition.value() instanceof Collection<?>)) invalid(condition.field());
        }
    }

    public boolean matches(ConditionGroup group, Map<String, Object> context) {
        validate(group);
        return switch (group.logic()) {
            case ALL -> group.conditions().stream().allMatch(condition -> matches(condition, context));
            case ANY -> group.conditions().stream().anyMatch(condition -> matches(condition, context));
        };
    }

    private boolean matches(Condition condition, Map<String, Object> context) {
        Object actual = context.get(condition.field());
        Object expected = condition.value();
        if (actual == null || expected == null) {
            if (condition.operator() == WorkflowV2Dtos.Operator.EQ) return actual == expected;
            if (condition.operator() == WorkflowV2Dtos.Operator.NE) return actual != expected;
            return false;
        }
        return switch (condition.operator()) {
            case EQ -> comparable(actual).compareTo(comparable(expected)) == 0;
            case NE -> comparable(actual).compareTo(comparable(expected)) != 0;
            case GT -> comparable(actual).compareTo(comparable(expected)) > 0;
            case GTE -> comparable(actual).compareTo(comparable(expected)) >= 0;
            case LT -> comparable(actual).compareTo(comparable(expected)) < 0;
            case LTE -> comparable(actual).compareTo(comparable(expected)) <= 0;
            case IN -> ((Collection<?>) expected).stream()
                    .map(this::comparable).anyMatch(value -> value.compareTo(comparable(actual)) == 0);
            case CONTAINS -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
        };
    }

    private ComparableValue comparable(Object value) {
        if (value == null) return new ComparableValue(null, null);
        if (value instanceof Number || String.valueOf(value).matches("-?\\d+(\\.\\d+)?")) {
            return new ComparableValue(new BigDecimal(String.valueOf(value)), null);
        }
        return new ComparableValue(null, String.valueOf(value));
    }

    private void invalid(String field) {
        throw new PlatformApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_CONDITION_INVALID",
                "errors.workflowConditionInvalid", Map.of("field", field));
    }

    private record ComparableValue(BigDecimal number, String text) implements Comparable<ComparableValue> {
        @Override
        public int compareTo(ComparableValue other) {
            if (number != null && other.number != null) return number.compareTo(other.number);
            if (text == null && other.text == null) return 0;
            if (text == null) return -1;
            if (other.text == null) return 1;
            return text.compareToIgnoreCase(other.text);
        }
    }
}
