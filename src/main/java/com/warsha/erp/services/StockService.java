package com.warsha.erp.services;

import com.warsha.erp.entities.OrderItems;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductVariant;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.InsufficientStockException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductVariantRepository;
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
 *
 * Products with sizes/colors keep stock per {@link ProductVariant}. The product's own
 * quantity stays the total of its variants, so lists and reports keep working. Variant
 * rows are only changed while their product row is locked.
 */
@Service
public class StockService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public StockService(ProductRepository productRepository, ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
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

    /**
     * Removes {@code quantity} from one size/color and from the product total.
     * The product must already be locked.
     */
    public void take(Product product, ProductVariant variant, int quantity) {
        if (variant == null) {
            take(product, quantity);
            return;
        }
        requirePositive(product, quantity);
        if (isDeleted(product)) {
            throw new BusinessRuleException("'" + product.getName() + "' is no longer available.");
        }
        String name = product.getName() + " (" + variant.label() + ")";
        if (variant.isDeleted()) {
            throw new BusinessRuleException("'" + name + "' is no longer available.");
        }
        int available = available(variant);
        if (available < quantity) {
            throw new InsufficientStockException(available == 0
                    ? "'" + name + "' is out of stock."
                    : "Not enough stock for '" + name + "' (available: " + available
                      + ", requested: " + quantity + ").");
        }
        LocalDateTime now = LocalDateTime.now();
        variant.setQuantity(available - quantity);
        variant.setSold(soldOf(variant) + quantity);
        variant.setUpdatedAt(now);
        product.setQuantity(String.valueOf(Math.max(0, available(product) - quantity)));
        product.setSold(sold(product) + quantity);
        product.setUpdatedAt(now);
    }

    /** Returns {@code quantity} to stock and removes it from the sold count. */
    public void putBack(Product product, int quantity) {
        requirePositive(product, quantity);
        product.setQuantity(String.valueOf(available(product) + quantity));
        product.setSold(Math.max(0, sold(product) - quantity));
        product.setUpdatedAt(LocalDateTime.now());
    }

    /** Returns {@code quantity} to one size/color and to the product total. */
    public void putBack(Product product, ProductVariant variant, int quantity) {
        putBack(product, quantity);
        if (variant != null) {
            variant.setQuantity(available(variant) + quantity);
            variant.setSold(Math.max(0, soldOf(variant) - quantity));
            variant.setUpdatedAt(LocalDateTime.now());
        }
    }

    /** Puts back every line of an order. The products must already be locked. */
    public void putBackAll(Collection<OrderItems> items) {
        for (OrderItems item : items) {
            putBack(item.getProduct(), item.getVariant(), item.getQuantity());
        }
    }

    /**
     * Finds the size/color an order line asks for. Products with variants need one;
     * products without variants must not get one.
     *
     * @return the variant, or null for a product without variants
     */
    public ProductVariant resolveVariant(Product product, Long variantId) {
        List<ProductVariant> variants = variantRepository.findActiveByProductId(product.getProductID());
        if (variantId == null) {
            if (variants.isEmpty()) return null;
            throw new ValidationException("items", "Choose a size/color for '" + product.getName() + "'.");
        }
        return variantRepository.findById(variantId)
                .filter(v -> v.getProduct().getProductID().equals(product.getProductID()))
                .orElseThrow(() -> new ValidationException("items",
                        "The chosen size/color doesn't belong to '" + product.getName() + "'."));
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

    public static int available(ProductVariant variant) {
        return variant.getQuantity() != null ? variant.getQuantity() : 0;
    }

    private static int soldOf(ProductVariant variant) {
        return variant.getSold() != null ? variant.getSold() : 0;
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
