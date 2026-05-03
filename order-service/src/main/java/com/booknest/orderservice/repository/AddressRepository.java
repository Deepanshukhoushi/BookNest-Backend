package com.booknest.orderservice.repository;

import com.booknest.orderservice.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByCustomerId(Long customerId);
    List<Address> findByCustomerIdAndIsActiveTrue(Long customerId);
    List<Address> findByCity(String city);
    Optional<Address> findByAddressId(Long addressId);
    Optional<Address> findByAddressIdAndCustomerIdAndIsActiveTrue(Long addressId, Long customerId);
    void deleteByCustomerId(Long customerId);
}
