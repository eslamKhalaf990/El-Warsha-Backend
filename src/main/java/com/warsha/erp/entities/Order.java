package com.warsha.erp.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "Orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OrderID")
    private Long id;

    @Column(name="OrderDate", nullable = false)
    private LocalDate orderDate;

    @Column(name= "Status", nullable = false)
    @Convert(converter = OrderStatusConverter.class)
    private OrderStatus status;

    @Column(name= "OrderSource", nullable = false)
    private String OrderSource;

    @Column(name= "Discount", nullable = false)
    private Double Discount;

    @Column(name= "TotalPrice", nullable = false)
    private Double TotalPrice;

    @Column(name= "DeliveryCharge", nullable = false)
    private Double DeliveryCharge;

    @Column(name= "Notes")
    private String Notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID", nullable = false)
    private Customer customer;

    @OneToOne
    @JoinColumn(name = "InvoiceID", unique = true)  // ensures 1-to-1
    @JsonBackReference
    private Invoice invoice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderItems> items;

//    @Override
//    public String toString() {
//        return "Order{" +
//                ", orderDate=" + orderDate +
//                ", status='" + status + '\'' +
//                ", OrderSource='" + OrderSource + '\'' +
//                ", Discount=" + Discount +
//                ", TotalPrice=" + TotalPrice +
//                ", DeliveryCharge=" + DeliveryCharge +
//                ", Notes='" + Notes + '\'' +
//                ", customer=" + (customer != null ? customer.getId() : null) +
//                '}';
//    }
}
