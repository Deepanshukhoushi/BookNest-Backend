package com.booknest.orderservice.repository;

import com.booknest.orderservice.entity.Address;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AddressRepositoryTest {

    @Autowired
    private AddressRepository addressRepository;

    @Test
    void testSaveAndFindByCustomerId() {
        Address address = Address.builder()
                .customerId(1L)
                .fullName("Jane Doe")
                .mobileNumber("9876543210")
                .flatNumber("123")
                .city("Chicago")
                .state("IL")
                .pincode("606010")
                .isActive(true)
                .build();
        addressRepository.save(address);

        List<Address> addresses = addressRepository.findByCustomerId(1L);
        assertThat(addresses).isNotEmpty();
        assertThat(addresses.get(0).getFullName()).isEqualTo("Jane Doe");
    }

    @Test
    void testFindByCustomerIdAndIsActiveTrue() {
        Address address = Address.builder()
                .customerId(2L)
                .fullName("Active User")
                .mobileNumber("1234567890")
                .flatNumber("1")
                .city("City")
                .state("ST")
                .pincode("123456")
                .isActive(true)
                .build();
        addressRepository.save(address);

        Address inactiveAddress = Address.builder()
                .customerId(2L)
                .fullName("Inactive User")
                .mobileNumber("0987654321")
                .flatNumber("2")
                .city("City")
                .state("ST")
                .pincode("123456")
                .isActive(false)
                .build();
        addressRepository.save(inactiveAddress);

        List<Address> activeAddresses = addressRepository.findByCustomerIdAndIsActiveTrue(2L);
        assertThat(activeAddresses).hasSize(1);
        assertThat(activeAddresses.get(0).getFullName()).isEqualTo("Active User");
    }

    @Test
    void testFindByCity() {
        Address address = Address.builder()
                .customerId(3L)
                .fullName("City Test")
                .mobileNumber("1234567890")
                .flatNumber("1")
                .city("Seattle")
                .state("WA")
                .pincode("981010")
                .build();
        addressRepository.save(address);

        List<Address> addresses = addressRepository.findByCity("Seattle");
        assertThat(addresses).isNotEmpty();
    }

    @Test
    void testFindByAddressId() {
        Address address = Address.builder()
                .customerId(4L)
                .fullName("Address ID Test")
                .mobileNumber("1234567890")
                .flatNumber("1")
                .city("City")
                .state("ST")
                .pincode("123456")
                .build();
        Address saved = addressRepository.save(address);

        Optional<Address> found = addressRepository.findByAddressId(saved.getAddressId());
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Address ID Test");
    }

    @Test
    void testDeleteByCustomerId() {
        Address address = Address.builder()
                .customerId(5L)
                .fullName("Delete Test")
                .mobileNumber("1234567890")
                .flatNumber("1")
                .city("City")
                .state("ST")
                .pincode("123456")
                .build();
        addressRepository.save(address);

        addressRepository.deleteByCustomerId(5L);
        List<Address> addresses = addressRepository.findByCustomerId(5L);
        assertThat(addresses).isEmpty();
    }
}
