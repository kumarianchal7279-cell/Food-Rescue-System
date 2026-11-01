package org.goodroots.foodrescue.donation;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    @Query("""
        select d from Donation d join fetch d.donor
        where d.status = :status and d.safeConsumptionDeadline > :now
          and (:category is null or lower(d.category) = lower(:category))
          and (:location is null or lower(d.pickupAddress) like lower(concat('%', :location, '%')))
        order by d.safeConsumptionDeadline asc
        """)
    List<Donation> findAvailable(@Param("status") DonationStatus status, @Param("now") LocalDateTime now,
                                 @Param("category") String category, @Param("location") String location);

    @Query("select d from Donation d join fetch d.donor where d.donor.id = :donorId order by d.createdAt desc")
    List<Donation> findByDonorId(@Param("donorId") Long donorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d join fetch d.donor where d.id = :id")
    Optional<Donation> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update Donation d set d.status = :expired where d.status in :openStatuses and d.safeConsumptionDeadline <= :now")
    int expireOverdue(@Param("openStatuses") List<DonationStatus> openStatuses,
                      @Param("expired") DonationStatus expired, @Param("now") LocalDateTime now);
}
