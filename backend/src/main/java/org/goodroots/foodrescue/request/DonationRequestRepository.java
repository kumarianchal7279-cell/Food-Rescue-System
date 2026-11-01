package org.goodroots.foodrescue.request;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.goodroots.foodrescue.donation.DonationStatus;

public interface DonationRequestRepository extends JpaRepository<DonationRequest, Long> {
    @Query("""
        select (count(r) > 0) from DonationRequest r
        where r.donation.id = :donationId and r.receiver.id = :receiverId and r.status in :statuses
        """)
    boolean hasActiveOrCompletedRequest(@Param("donationId") Long donationId,
                                        @Param("receiverId") Long receiverId,
                                        @Param("statuses") List<RequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DonationRequest r join fetch r.donation d join fetch d.donor join fetch r.receiver where r.id = :id")
    Optional<DonationRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select r from DonationRequest r join fetch r.donation d join fetch d.donor join fetch r.receiver where r.receiver.id = :userId order by r.requestedAt desc")
    List<DonationRequest> findByReceiverId(@Param("userId") Long userId);

    @Query("select r from DonationRequest r join fetch r.donation d join fetch d.donor join fetch r.receiver where d.donor.id = :userId order by r.requestedAt desc")
    List<DonationRequest> findByDonorId(@Param("userId") Long userId);

    @Query("select r from DonationRequest r join fetch r.donation d join fetch d.donor join fetch r.receiver where d.volunteer.id = :userId order by r.requestedAt desc")
    List<DonationRequest> findByVolunteerId(@Param("userId") Long userId);

    @Modifying
    @Query("update DonationRequest r set r.status = :rejected where r.status in :openStatuses and r.donation.status = :expired")
    int rejectExpiredRequests(@Param("openStatuses") List<RequestStatus> openStatuses,
                              @Param("rejected") RequestStatus rejected, @Param("expired") DonationStatus expired);
}
