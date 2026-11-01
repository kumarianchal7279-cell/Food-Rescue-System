package org.goodroots.foodrescue.donation;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class DonationDtos {
    private DonationDtos() {}

    public record CreateDonationRequest(
        @NotBlank @Size(max = 120) String foodName,
        @NotBlank @Size(max = 60) @jakarta.validation.constraints.Pattern(regexp = "(?i)^(produce|bakery|prepared|dairy|other)$", message = "must be a supported food category") String category,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 6, fraction = 2) BigDecimal quantityKg,
        @NotNull LocalDateTime preparationTime,
        @NotNull @Future LocalDateTime safeConsumptionDeadline,
        @NotBlank @Size(max = 300) String pickupAddress,
        @Size(max = 1000) String imageUrl,
        @Size(max = 500) String storageConditions,
        @Size(max = 500) String allergens,
        @Size(max = 1000) String handlingInformation,
        @AssertTrue(message = "Confirm the food-safety checklist before sharing.") boolean foodSafetyChecklistConfirmed
    ) {}

    public record DonationResponse(
        long id, String foodName, String category, String description, BigDecimal quantityKg,
        LocalDateTime preparationTime, LocalDateTime safeConsumptionDeadline, String pickupAddress,
        String imageUrl, String storageConditions, String allergens, String handlingInformation,
        boolean foodSafetyChecklistConfirmed, DonationStatus status, long donorId, String donorName,
        String organizationName, Long volunteerId, LocalDateTime createdAt
    ) {
        public static DonationResponse from(Donation donation) {
            return new DonationResponse(donation.getId(), donation.getFoodName(), donation.getCategory(),
                donation.getDescription(), donation.getQuantityKg(), donation.getPreparationTime(),
                donation.getSafeConsumptionDeadline(), donation.getPickupAddress(), donation.getImageUrl(),
                donation.getStorageConditions(), donation.getAllergens(), donation.getHandlingInformation(),
                donation.isFoodSafetyChecklistConfirmed(), donation.getStatus(), donation.getDonor().getId(), donation.getDonor().getFullName(),
                donation.getDonor().getOrganizationName(),
                donation.getVolunteer() == null ? null : donation.getVolunteer().getId(), donation.getCreatedAt());
        }
    }
}
