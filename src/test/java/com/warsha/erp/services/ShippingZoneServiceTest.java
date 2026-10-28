package com.warsha.erp.services;

import com.warsha.erp.entities.ShippingZone;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ShippingZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShippingZoneServiceTest {

    private final ShippingZoneRepository repository = mock(ShippingZoneRepository.class);
    private final ShippingZoneService service = new ShippingZoneService(repository);
    private final List<ShippingZone> zones = new ArrayList<>();

    private ShippingZone zone(int id, String name, double fee, boolean active) {
        ShippingZone z = new ShippingZone();
        z.setId(id);
        z.setCityName(name);
        z.setShippingFee(BigDecimal.valueOf(fee));
        z.setIsActive(active);
        zones.add(z);
        return z;
    }

    @BeforeEach
    void setUp() {
        when(repository.findAllByOrderByIdAsc()).thenAnswer(inv -> List.copyOf(zones));
        when(repository.findAll()).thenAnswer(inv -> List.copyOf(zones));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void theStoreSeesOnlyActiveZonesWithTheirCurrentPrices() {
        zone(1, "القاهرة", 80, true);
        zone(2, "مطروح", 130, false);
        ShippingZone alex = zone(3, "الإسكندرية", 90, true);

        alex.setShippingFee(BigDecimal.valueOf(95));   // edited in the ERP

        assertThat(service.activePriceMap()).containsExactly(
                org.assertj.core.api.Assertions.entry("القاهرة", 80.0),
                org.assertj.core.api.Assertions.entry("الإسكندرية", 95.0));
        assertThat(service.deliveryPrice("الإسكندرية")).isEqualTo(95.0);
    }

    @Test
    void spellingVariantsFindTheSameGovernorate() {
        zone(1, "الإسكندرية", 90, true);
        zone(2, "الجيزة", 80, true);
        assertThat(service.deliveryPrice("الاسكندريه")).isEqualTo(90.0);
        assertThat(service.deliveryPrice(" اسكندرية ")).isEqualTo(90.0);
        assertThat(service.deliveryPrice("جيزة")).isEqualTo(80.0);
    }

    @Test
    void unknownOrInactiveGovernoratesCannotBeDeliveredTo() {
        zone(1, "مطروح", 130, false);
        assertThat(service.isValid("مطروح")).isFalse();
        assertThat(service.isValid(null)).isFalse();
        assertThatThrownBy(() -> service.deliveryPrice("مطروح"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("We don't deliver to 'مطروح' yet");
    }

    @Test
    void aGovernorateCanOnlyHaveOnePrice() {
        zone(1, "الإسكندرية", 90, true);
        ShippingZone dup = new ShippingZone();
        dup.setCityName("الاسكندريه");
        dup.setShippingFee(BigDecimal.TEN);
        assertThatThrownBy(() -> service.saveShippingZone(dup)).isInstanceOf(ConflictException.class);
    }

    @Test
    void pricesCannotBeNegative() {
        zone(1, "القاهرة", 80, true);
        when(repository.findById(1)).thenReturn(Optional.of(zones.get(0)));
        assertThatThrownBy(() -> service.updateShippingPrice(1, BigDecimal.valueOf(-5)))
                .isInstanceOf(ValidationException.class);
        assertThat(service.updateShippingPrice(1, BigDecimal.valueOf(85)).getShippingFee()).isEqualByComparingTo("85");
    }
}
