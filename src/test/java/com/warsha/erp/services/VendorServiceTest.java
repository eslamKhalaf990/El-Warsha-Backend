package com.warsha.erp.services;

import com.warsha.erp.dtos.VendorRequest;
import com.warsha.erp.entities.Vendor;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VendorServiceTest {

    private final VendorRepository repository = mock(VendorRepository.class);
    private final VendorService service = new VendorService(repository);
    private final List<Vendor> rows = new ArrayList<>();

    private Vendor vendor(long id, String name, Boolean active) {
        Vendor v = new Vendor();
        v.setVendorId(id);
        v.setName(name);
        v.setIsActive(active);
        rows.add(v);
        when(repository.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private static VendorRequest request(String name) {
        VendorRequest r = new VendorRequest();
        r.setName(name);
        return r;
    }

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenAnswer(inv -> List.copyOf(rows));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void archivedVendorsAreHiddenUnlessAskedFor() {
        vendor(1, "Silver Co", true);
        vendor(2, "Old Co", false);
        vendor(3, "Legacy Co", null);   // row from before IsActive existed

        assertThat(service.getAllVendors(false)).extracting(Vendor::getName).containsExactly("Silver Co", "Legacy Co");
        assertThat(service.getAllVendors(true)).hasSize(3);
    }

    @Test
    void createTidiesTextAndStartsActive() {
        VendorRequest r = request("  Silver   Co ");
        r.setPhone("  ");
        r.setNotes("  pays monthly \n");

        Vendor saved = service.create(r);

        assertThat(saved.getName()).isEqualTo("Silver Co");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getNotes()).isEqualTo("pays monthly");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateActiveNamesAreRejectedOnTheNameField() {
        vendor(1, "Silver Co", true);

        assertThatThrownBy(() -> service.create(request("silver co")))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((AppException) e).getDetails().get(0).field()).isEqualTo("name"));
    }

    @Test
    void anArchivedVendorsNameCanBeReused() {
        vendor(1, "Silver Co", false);

        assertThat(service.create(request("Silver Co")).getName()).isEqualTo("Silver Co");
    }

    @Test
    void updateKeepsItsOwnNameAndStampsUpdatedAt() {
        vendor(1, "Silver Co", true);

        Vendor updated = service.update(1L, request("SILVER CO"));

        assertThat(updated.getName()).isEqualTo("SILVER CO");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void missingVendorsAreNotFound() {
        assertThatThrownBy(() -> service.update(9L, request("x"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.archive(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteArchivesInsteadOfRemoving() {
        Vendor v = vendor(1, "Silver Co", true);

        service.archive(1L);

        assertThat(v.getIsActive()).isFalse();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }
}
