package org.goodroots.foodrescue.donation;

import java.time.LocalDateTime;
import java.util.List;
import org.goodroots.foodrescue.common.ApiException;
import org.goodroots.foodrescue.request.DonationRequestRepository;
import org.goodroots.foodrescue.request.RequestStatus;
import org.goodroots.foodrescue.user.User;
import org.goodroots.foodrescue.user.UserRepository;
import org.goodroots.foodrescue.user.UserRole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DonationService {
    private final DonationRepository donations;
    private final UserRepository users;
    private final DonationRequestRepository requests;

    public DonationService(DonationRepository donations, UserRepository users, DonationRequestRepository requests) {
        this.donations = donations;
        this.users = users;
        this.requests = requests;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public DonationDtos.DonationResponse create(String email, DonationDtos.CreateDonationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (request.preparationTime().isAfter(now)) {
            throw ApiException.badRequest("Preparation time cannot be in the future.");
        }
        if (!request.safeConsumptionDeadline().isAfter(request.preparationTime())) {
            throw ApiException.badRequest("The consumption deadline must be after preparation time.");
        }
        User donor = findUser(email);
        Donation donation = new Donation(donor, request.foodName().trim(), request.category().trim(),
            request.description(), request.quantityKg(), request.preparationTime(),
            request.safeConsumptionDeadline(), request.pickupAddress().trim(), request.imageUrl(),
            request.storageConditions(), request.allergens(), request.handlingInformation(),
            request.foodSafetyChecklistConfirmed());
        return DonationDtos.DonationResponse.from(donations.save(donation));
    }

    @Transactional(readOnly = true)
    public List<DonationDtos.DonationResponse> available(String category, String location) {
        String normalizedCategory = blankToNull(category);
        String normalizedLocation = blankToNull(location);
        return donations.findAvailable(DonationStatus.AVAILABLE, LocalDateTime.now(),
                normalizedCategory, normalizedLocation).stream()
            .map(DonationDtos.DonationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DonationDtos.DonationResponse> mine(String email) {
        User user = findUser(email);
        return donations.findByDonorId(user.getId()).stream().map(DonationDtos.DonationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DonationDtos.DonationResponse get(long id, String email) {
        Donation donation = donations.findById(id)
            .orElseThrow(() -> ApiException.notFound("Donation was not found."));
        User user = findUser(email);
        boolean isDonor = donation.getDonor().getId().equals(user.getId());
        boolean isAssignedVolunteer = donation.getVolunteer() != null
            && donation.getVolunteer().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        boolean isAssociatedReceiver = user.getRole() == UserRole.NGO
            && requests.hasActiveOrCompletedRequest(donation.getId(), user.getId(),
                List.of(RequestStatus.REQUESTED, RequestStatus.ACCEPTED, RequestStatus.COLLECTED, RequestStatus.DELIVERED));
        if (!isDonor && !isAssignedVolunteer && !isAdmin && !isAssociatedReceiver) {
            throw ApiException.notFound("Donation was not found.");
        }
        return DonationDtos.DonationResponse.from(donation);
    }

    @Scheduled(fixedDelayString = "${app.donations.expiration-check-ms:60000}")
    @Transactional
    public void expireAvailableDonations() {
        donations.expireOverdue(List.of(DonationStatus.AVAILABLE, DonationStatus.REQUESTED,
            DonationStatus.ACCEPTED, DonationStatus.PICKUP_ASSIGNED), DonationStatus.EXPIRED, LocalDateTime.now());
        requests.rejectExpiredRequests(List.of(RequestStatus.REQUESTED, RequestStatus.ACCEPTED),
            RequestStatus.REJECTED, DonationStatus.EXPIRED);
    }

    private User findUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> ApiException.unauthorized("Account is no longer available."));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
