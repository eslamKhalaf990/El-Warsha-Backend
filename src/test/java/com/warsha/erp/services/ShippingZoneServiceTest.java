package com.warsha.erp.services;

import com.warsha.erp.dtos.ShippingZoneRequest;
import com.warsha.erp.entities.ShippingZone;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.NotFoundException;
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

    private static ShippingZoneRequest request(String name, double fee, Boolean active) {
        ShippingZoneRequest r = new ShippingZoneRequest();
        r.setCityName(name);
        r.setShippingFee(BigDecimal.valueOf(fee));
        r.setIsActive(active);
        return r;
    }

    @Test
    void aGovernorateCanOnlyHaveOnePrice() {
        zone(1, "الإسكندرية", 90, true);
        assertThatThrownBy(() -> service.create(request("الاسكندريه", 10, null)))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((AppException) e).getDetails().get(0).field()).isEqualTo("cityName"));
    }

    @Test
    void newZonesStartActive() {
        assertThat(service.create(request("  مطروح ", 130, null)).getIsActive()).isTrue();
    }

    @Test
    void pricesCannotBeNegative() {
        zone(1, "القاهرة", 80, true);
        when(repository.findById(1)).thenReturn(Optional.of(zones.get(0)));
        assertThatThrownBy(() -> service.update(1, request("القاهرة", -5, null)))
                .isInstanceOf(ValidationException.class);
        assertThat(service.update(1, request("القاهرة", 85, null)).getShippingFee()).isEqualByComparingTo("85");
    }

    @Test
    void updateCanRenameAndDeactivateAZone() {
        ShippingZone cairo = zone(1, "القاهرة", 80, true);
        when(repository.findById(1)).thenReturn(Optional.of(cairo));

        service.update(1, request("القاهره", 80, false));

        assertThat(cairo.getCityName()).isEqualTo("القاهره");
        assertThat(cairo.getIsActive()).isFalse();
        assertThat(service.isValid("القاهرة")).isFalse();
    }

    @Test
    void missingZonesAreNotFound() {
        when(repository.findById(9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getShippingZone(9)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(9, request("x", 1, null))).isInstanceOf(NotFoundException.class);
    }
}
