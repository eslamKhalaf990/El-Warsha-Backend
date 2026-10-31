package com.warsha.erp.repository;

import com.warsha.erp.entities.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Integer> {

    List<ShippingZone> findAllByOrderByIdAsc();
}
