package com.booknest.orderservice.repository;

import com.booknest.orderservice.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    List<Coupon> findAllByActiveTrue();

    boolean existsByCodeIgnoreCase(String code);
}
