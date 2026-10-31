package com.warsha.erp.entities;

import com.warsha.erp.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.warsha.erp.entities.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Test
    void movesForwardAndMaySkipSteps() {
        assertThat(PENDING.canMoveTo(PROCESSING)).isTrue();
        assertThat(PENDING.canMoveTo(COMPLETED)).isTrue();
        assertThat(PROCESSING.canMoveTo(SHIPPED)).isTrue();
        assertThat(SHIPPED.canMoveTo(COMPLETED)).isTrue();
    }

    @Test
    void neverMovesBackwardsOrToItself() {
        assertThat(SHIPPED.canMoveTo(PENDING)).isFalse();
        assertThat(PROCESSING.canMoveTo(PROCESSING)).isFalse();
    }

    @Test
    void cancelOnlyBeforeCompletion() {
        assertThat(PENDING.canMoveTo(CANCELLED)).isTrue();
        assertThat(SHIPPED.canMoveTo(CANCELLED)).isTrue();
        assertThat(COMPLETED.canMoveTo(CANCELLED)).isFalse();
    }

    @Test
    void finalStatesAreFinal() {
        assertThat(COMPLETED.allowedNext()).isEmpty();
        assertThat(CANCELLED.allowedNext()).isEmpty();
        assertThat(PROCESSING.allowedNext()).isEqualTo(List.of(SHIPPED, COMPLETED, CANCELLED));
    }

    @Test
    void onlyEditableBeforeShipping() {
        assertThat(PENDING.isEditable()).isTrue();
        assertThat(PROCESSING.isEditable()).isTrue();
        assertThat(SHIPPED.isEditable()).isFalse();
        assertThat(CANCELLED.isEditable()).isFalse();
    }

    @Test
    void parsesLabelsNamesAndLegacySpelling() {
        assertThat(OrderStatus.from("Shipped")).isEqualTo(SHIPPED);
        assertThat(OrderStatus.from("shipped")).isEqualTo(SHIPPED);
        assertThat(OrderStatus.from("CANCELLED")).isEqualTo(CANCELLED);
        assertThat(OrderStatus.from(" Canceled ")).isEqualTo(CANCELLED);
    }

    @Test
    void rejectsUnknownStatusWithFriendlyMessage() {
        assertThatThrownBy(() -> OrderStatus.from("Delivered"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("'Delivered' is not a valid order status")
                .hasMessageContaining("Pending, Processing, Shipped, Completed, Cancelled");
    }

    @Test
    void storedAsTheExistingLabels() {
        OrderStatusConverter converter = new OrderStatusConverter();
        assertThat(converter.convertToDatabaseColumn(CANCELLED)).isEqualTo("Cancelled");
        assertThat(converter.convertToEntityAttribute("Completed")).isEqualTo(COMPLETED);
    }
}
