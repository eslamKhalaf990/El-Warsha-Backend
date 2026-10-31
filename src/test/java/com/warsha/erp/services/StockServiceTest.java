package com.warsha.erp.services;

import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductVariant;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.InsufficientStockException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StockServiceTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final StockService stock = new StockService(repository, variants);

    static Product product(long id, String quantity, Integer sold) {
        Product p = new Product();
        p.setProductID(id);
        p.setName("Bracelet " + id);
        p.setQuantity(quantity);
        p.setSold(sold);
        return p;
    }

    static ProductVariant variant(long id, Product product, String size, String color, int quantity) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setProduct(product);
        v.setSize(size);
        v.setColor(color);
        v.setQuantity(quantity);
        v.setSold(0);
        return v;
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

    @Test
    void takingAVariantReducesItAndTheProductTotal() {
        Product p = product(1, "5", 0);
        ProductVariant m = variant(10, p, "M", "Black", 3);

        stock.take(p, m, 2);

        assertThat(m.getQuantity()).isEqualTo(1);
        assertThat(m.getSold()).isEqualTo(2);
        assertThat(p.getQuantity()).isEqualTo("3");
        assertThat(p.getSold()).isEqualTo(2);
    }

    @Test
    void variantStockIsCheckedOnItsOwn() {
        Product p = product(1, "9", 0);
        ProductVariant m = variant(10, p, "M", "Black", 1);
        assertThatThrownBy(() -> stock.take(p, m, 2))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Bracelet 1 (M / Black)' (available: 1, requested: 2).");
    }

    @Test
    void deletedVariantsCannotBeOrdered() {
        Product p = product(1, "9", 0);
        ProductVariant m = variant(10, p, "M", null, 5);
        m.setDeleted(true);
        assertThatThrownBy(() -> stock.take(p, m, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("'Bracelet 1 (M)' is no longer available.");
    }

    @Test
    void putBackRestoresTheVariantToo() {
        Product p = product(1, "1", 2);
        ProductVariant m = variant(10, p, "M", null, 0);
        m.setSold(2);

        stock.putBack(p, m, 2);

        assertThat(m.getQuantity()).isEqualTo(2);
        assertThat(m.getSold()).isZero();
        assertThat(p.getQuantity()).isEqualTo("3");
    }

    @Test
    void productsWithVariantsNeedOneChosen() {
        Product p = product(1, "5", 0);
        when(variants.findActiveByProductId(1L)).thenReturn(List.of(variant(10, p, "M", null, 5)));
        assertThatThrownBy(() -> stock.resolveVariant(p, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Choose a size/color for 'Bracelet 1'.");
    }

    @Test
    void productsWithoutVariantsResolveToNull() {
        when(variants.findActiveByProductId(1L)).thenReturn(List.of());
        assertThat(stock.resolveVariant(product(1, "5", 0), null)).isNull();
    }

    @Test
    void aVariantOfAnotherProductIsRejected() {
        Product p = product(1, "5", 0);
        when(variants.findById(20L)).thenReturn(Optional.of(variant(20, product(2, "5", 0), "L", null, 5)));
        assertThatThrownBy(() -> stock.resolveVariant(p, 20L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("The chosen size/color doesn't belong to 'Bracelet 1'.");
    }
}
