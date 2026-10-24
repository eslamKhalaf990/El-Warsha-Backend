package com.warsha.erp.services;

import com.warsha.erp.entities.Product;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.InsufficientStockException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StockServiceTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final StockService stock = new StockService(repository);

    static Product product(long id, String quantity, Integer sold) {
        Product p = new Product();
        p.setProductID(id);
        p.setName("Bracelet " + id);
        p.setQuantity(quantity);
        p.setSold(sold);
        return p;
    }

    @Test
    void takeReducesStockAndCountsSold() {
        Product p = product(1, "5", 2);
        stock.take(p, 3);
        assertThat(p.getQuantity()).isEqualTo("2");
        assertThat(p.getSold()).isEqualTo(5);
    }

    @Test
    void takeRefusesToOversell() {
        Product p = product(1, "2", 0);
        assertThatThrownBy(() -> stock.take(p, 5))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Bracelet 1' (available: 2, requested: 5).");
        assertThat(p.getQuantity()).isEqualTo("2");
    }

    @Test
    void outOfStockHasItsOwnMessage() {
        assertThatThrownBy(() -> stock.take(product(1, "0", 0), 1))
                .hasMessage("'Bracelet 1' is out of stock.");
    }

    @Test
    void deletedProductsCannotBeOrdered() {
        Product p = product(1, "9", 0);
        p.setDeleted("true");
        assertThatThrownBy(() -> stock.take(p, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("'Bracelet 1' is no longer available.");
    }

    @Test
    void putBackRestoresStockAndSold() {
        Product p = product(1, "2", 5);
        stock.putBack(p, 3);
        assertThat(p.getQuantity()).isEqualTo("5");
        assertThat(p.getSold()).isEqualTo(2);
    }

    @Test
    void soldNeverGoesNegative() {
        Product p = product(1, "0", null);
        stock.putBack(p, 2);
        assertThat(p.getSold()).isZero();
    }

    @Test
    void zeroOrNegativeQuantityIsAValidationError() {
        assertThatThrownBy(() -> stock.take(product(1, "5", 0), 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> stock.putBack(product(1, "5", 0), -1)).isInstanceOf(ValidationException.class);
    }

    @Test
    void garbageQuantityIsReportedNotCrashed() {
        assertThatThrownBy(() -> stock.take(product(1, "five", 0), 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is 'five', which is not a number");
        assertThat(StockService.available(product(1, " ", 0))).isZero();
        assertThat(StockService.available(product(1, " 7 ", 0))).isEqualTo(7);
    }

    @Test
    void lockLocksEachProductOnceInIdOrder() {
        when(repository.findAllByIdWithLock(List.of(1L, 3L))).thenReturn(List.of(product(1, "1", 0), product(3, "1", 0)));

        assertThat(stock.lock(List.of(3L, 1L, 3L))).containsOnlyKeys(1L, 3L);
        verify(repository).findAllByIdWithLock(List.of(1L, 3L));
    }

    @Test
    void lockReportsMissingProducts() {
        when(repository.findAllByIdWithLock(List.of(1L, 2L))).thenReturn(List.of(product(1, "1", 0)));
        assertThatThrownBy(() -> stock.lock(List.of(1L, 2L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product #2 was not found.");
    }
}
