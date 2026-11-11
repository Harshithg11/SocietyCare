package com.societycare.repository;

import com.societycare.entity.Society;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocietyRepository extends JpaRepository<Society, Long> {

    boolean existsBySocietyNameIgnoreCaseAndPincode(String societyName, String pincode);
}
