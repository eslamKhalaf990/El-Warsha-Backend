package com.warsha.erp.services;

import com.warsha.erp.entities.OrderItems;
import com.warsha.erp.entities.Product;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.InsufficientStockException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The only place that changes product stock for orders.
 *
 * Callers lock every product an operation touches with {@link #lock}, then call
 * {@link #take} / {@link #putBack}. Changes are applied to the locked, managed
 * entities and flushed when the surrounding order transaction commits, so two
 * concurrent orders can never oversell the same product.
 */
@Service
public class StockService {

    private final ProductRepository productRepository;

    public StockService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Loads and row-locks the given products (in id order, to avoid deadlocks).
     *
     * @throws NotFoundException if any id does not exist
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Product> lock(Collection<Long> productIds) {
        List<Long> ids = List.copyOf(new TreeSet<>(productIds));
        Map<Long, Product> byId = new LinkedHashMap<>();
        for (Product p : productRepository.findAllByIdWithLock(ids)) {
            byId.put(p.getProductID(), p);
        }
        for (Long id : ids) {
            if (!byId.containsKey(id)) throw new NotFoundException("Product", id);
        }
        return byId;
    }

    /** Removes {@code quantity} from stock and counts it as sold. */
    public void take(Product product, int quantity) {
        requirePositive(product, quantity);
        if (isDeleted(product)) {
            throw new BusinessRuleException("'" + product.getName() + "' is no longer available.");
        }
        int available = available(product);
        if (available < quantity) {
            throw new InsufficientStockException(available == 0
                    ? "'" + product.getName() + "' is out of stock."
                    : "Not enough stock for '" + product.getName() + "' (available: " + available
                      + ", requested: " + quantity + ").");
        }
        product.setQuantity(String.valueOf(available - quantity));
        product.setSold(sold(product) + quantity);
        product.setUpdatedAt(LocalDateTime.now());
    }

    /** Returns {@code quantity} to stock and removes it from the sold count. */
    public void putBack(Product product, int quantity) {
        requirePositive(product, quantity);
        product.setQuantity(String.valueOf(available(product) + quantity));
        product.setSold(Math.max(0, sold(product) - quantity));
        product.setUpdatedAt(LocalDateTime.now());
    }

    /** Puts back every line of an order. The products must already be locked. */
    public void putBackAll(Collection<OrderItems> items) {
        for (OrderItems item : items) {
            putBack(item.getProduct(), item.getQuantity());
        }
    }

    /**
     * Current stock. Quantity is still stored as text; blank counts as 0 and
     * anything non-numeric is reported instead of crashing with a 500.
     */
    public static int available(Product product) {
        String raw = product.getQuantity();
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("The stock quantity of '" + product.getName() + "' is '" + raw
                    + "', which is not a number. Fix it on the product page and try again.");
        }
    }

    private static int sold(Product product) {
        return product.getSold() != null ? product.getSold() : 0;
    }

    private static boolean isDeleted(Product product) {
        return "true".equalsIgnoreCase(product.getDeleted());
    }

    private static void requirePositive(Product product, int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("quantity",
                    "Quantity for '" + product.getName() + "' must be at least 1.");
        }
    }
}
