package com.warsha.erp.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "Customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CustomerID")
    private Long id;

    @Column(name = "FullName")
    private String fullName;

    @Column(name = "Governorate")
    private String governorate;

    @Column(name = "Phone")
    private String phone;

    @Column(name = "City")
    private String city;

    @Column(name = "SecondaryPhone")
    private String secondaryPhone;

    @Column(name = "Address")
    private String address;

    @Column(name = "Email")
    private String email;

    // Accepted on sign-up, never sent back
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "Password")
    private String password;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "Status")
    private String status = "Active";

    @JsonIgnore
    @Column(name = "Otp")
    private String otp;

    @JsonIgnore
    @Column(name = "OtpExpirationTime")
    private LocalDateTime otpExpirationTime;

    @Override
    public String toString() {
        return "Customer{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", governorate='" + governorate + '\'' +
                ", phone='" + phone + '\'' +
                ", city='" + city + '\'' +
                ", secondaryPhone='" + secondaryPhone + '\'' +
                ", address='" + address + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", status='" + status + '\'' +
                ", otp='" + otp + '\'' +
                ", otpExpirationTime=" + otpExpirationTime +
                '}';
    }
}
