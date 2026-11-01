package org.goodroots.foodrescue.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.goodroots.foodrescue.donation.DonationDtos;
import org.goodroots.foodrescue.user.User;

public final class RequestDtos {
    private RequestDtos() {}

    public record CreateRequest(@Size(max = 500) String message) {}
    public record DecisionRequest(@NotNull Decision decision) {}
    public enum Decision { ACCEPT, REJECT }
    public record AssignVolunteerRequest(@NotNull Long volunteerId) {}
    public record DeliveryStatusRequest(@NotNull DeliveryStatus status) {}
    public enum DeliveryStatus { COLLECTED, DELIVERED }

    public record RequestResponse(long id, RequestStatus status, String message, long receiverId,
                                  String receiverName, long donorId, String donorName,
                                  DonationDtos.DonationResponse donation) {
        public static RequestResponse from(DonationRequest request) {
            User receiver = request.getReceiver();
            return new RequestResponse(request.getId(), request.getStatus(), request.getMessage(),
                receiver.getId(), receiver.getFullName(), request.getDonation().getDonor().getId(),
                request.getDonation().getDonor().getFullName(), DonationDtos.DonationResponse.from(request.getDonation()));
        }
    }
}
