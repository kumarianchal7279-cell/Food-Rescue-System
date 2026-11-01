package org.goodroots.foodrescue.request;

import java.time.LocalDateTime;
import java.util.List;
import org.goodroots.foodrescue.common.ApiException;
import org.goodroots.foodrescue.donation.Donation;
import org.goodroots.foodrescue.donation.DonationRepository;
import org.goodroots.foodrescue.donation.DonationStatus;
import org.goodroots.foodrescue.user.User;
import org.goodroots.foodrescue.user.UserRepository;
import org.goodroots.foodrescue.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DonationRequestService {
    private final DonationRequestRepository requests;
    private final DonationRepository donations;
    private final UserRepository users;

    public DonationRequestService(DonationRequestRepository requests, DonationRepository donations, UserRepository users) {
        this.requests = requests;
        this.donations = donations;
        this.users = users;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RequestDtos.RequestResponse create(long donationId, String email, RequestDtos.CreateRequest input) {
        User receiver = findUser(email);
        Donation donation = findDonationForUpdate(donationId);
        if (!donation.getSafeConsumptionDeadline().isAfter(LocalDateTime.now())) {
            donation.setStatus(DonationStatus.EXPIRED);
            throw ApiException.conflict("This donation has passed its listed collection deadline.");
        }
        if (donation.getStatus() != DonationStatus.AVAILABLE) {
            throw ApiException.conflict("This donation is no longer available to request.");
        }
        DonationRequest request = requests.save(new DonationRequest(donation, receiver,
            input == null || input.message() == null ? null : input.message().trim()));
        donation.setStatus(DonationStatus.REQUESTED);
        return RequestDtos.RequestResponse.from(request);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RequestDtos.RequestResponse decide(long requestId, String email, RequestDtos.Decision decision) {
        DonationRequest request = findRequestForUpdate(requestId);
        Donation donation = request.getDonation();
        requireDonor(request, email);
        if (request.getStatus() != RequestStatus.REQUESTED || donation.getStatus() != DonationStatus.REQUESTED) {
            throw ApiException.conflict("Only a pending request can be approved or rejected.");
        }
        if (decision == RequestDtos.Decision.ACCEPT) {
            if (!donation.getSafeConsumptionDeadline().isAfter(LocalDateTime.now())) {
                donation.setStatus(DonationStatus.EXPIRED);
                request.setStatus(RequestStatus.REJECTED);
                throw ApiException.conflict("This donation has passed its listed collection deadline.");
            }
            request.setStatus(RequestStatus.ACCEPTED);
            donation.setStatus(DonationStatus.ACCEPTED);
        } else {
            request.setStatus(RequestStatus.REJECTED);
            donation.setStatus(donation.getSafeConsumptionDeadline().isAfter(LocalDateTime.now())
                ? DonationStatus.AVAILABLE : DonationStatus.EXPIRED);
        }
        return RequestDtos.RequestResponse.from(request);
    }

    @Transactional
    public RequestDtos.RequestResponse assignVolunteer(long requestId, String email, long volunteerId) {
        DonationRequest request = findRequestForUpdate(requestId);
        Donation donation = request.getDonation();
        requireDonor(request, email);
        if (request.getStatus() != RequestStatus.ACCEPTED || donation.getStatus() != DonationStatus.ACCEPTED) {
            throw ApiException.conflict("A volunteer can only be assigned to an approved request.");
        }
        User volunteer = users.findById(volunteerId)
            .orElseThrow(() -> ApiException.notFound("Volunteer account was not found."));
        if (volunteer.getRole() != UserRole.VOLUNTEER) {
            throw ApiException.badRequest("The selected account is not registered as a volunteer.");
        }
        donation.setVolunteer(volunteer);
        donation.setStatus(DonationStatus.PICKUP_ASSIGNED);
        return RequestDtos.RequestResponse.from(request);
    }

    @Transactional
    public RequestDtos.RequestResponse updateDelivery(long requestId, String email,
            RequestDtos.DeliveryStatus nextStatus) {
        DonationRequest request = findRequestForUpdate(requestId);
        Donation donation = request.getDonation();
        if (donation.getVolunteer() == null || !donation.getVolunteer().getEmail().equals(email)) {
            throw ApiException.forbidden("Only the volunteer assigned to this pickup can update delivery status.");
        }
        if (nextStatus == RequestDtos.DeliveryStatus.COLLECTED) {
            if (donation.getStatus() != DonationStatus.PICKUP_ASSIGNED) {
                throw ApiException.conflict("Food can only be marked collected after a pickup is assigned.");
            }
            donation.setStatus(DonationStatus.COLLECTED);
            request.setStatus(RequestStatus.COLLECTED);
        } else {
            if (donation.getStatus() != DonationStatus.COLLECTED) {
                throw ApiException.conflict("Food can only be marked delivered after it has been collected.");
            }
            donation.setStatus(DonationStatus.DELIVERED);
            request.setStatus(RequestStatus.DELIVERED);
        }
        return RequestDtos.RequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<RequestDtos.RequestResponse> mine(String email) {
        User user = findUser(email);
        List<DonationRequest> results = switch (user.getRole()) {
            case DONOR -> requests.findByDonorId(user.getId());
            case NGO -> requests.findByReceiverId(user.getId());
            case VOLUNTEER -> requests.findByVolunteerId(user.getId());
            case ADMIN -> List.of();
        };
        return results.stream().map(RequestDtos.RequestResponse::from).toList();
    }

    private User findUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> ApiException.unauthorized("Account is no longer available."));
    }

    private Donation findDonationForUpdate(long id) {
        return donations.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Donation was not found."));
    }

    private DonationRequest findRequestForUpdate(long id) {
        return requests.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Donation request was not found."));
    }

    private void requireDonor(DonationRequest request, String email) {
        if (!request.getDonation().getDonor().getEmail().equals(email)) {
            throw ApiException.forbidden("Only the donor who posted this food can manage its request.");
        }
    }
}
