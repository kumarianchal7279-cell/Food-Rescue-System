package org.goodroots.foodrescue.request;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DonationRequestController {
    private final DonationRequestService requestService;

    public DonationRequestController(DonationRequestService requestService) { this.requestService = requestService; }

    @PostMapping("/donations/{donationId}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NGO')")
    public RequestDtos.RequestResponse create(@PathVariable long donationId,
            @AuthenticationPrincipal String email, @Valid @RequestBody(required = false) RequestDtos.CreateRequest body) {
        return requestService.create(donationId, email, body);
    }

    @GetMapping("/requests/mine")
    @PreAuthorize("hasAnyRole('DONOR', 'NGO', 'VOLUNTEER')")
    public List<RequestDtos.RequestResponse> mine(@AuthenticationPrincipal String email) {
        return requestService.mine(email);
    }

    @PatchMapping("/requests/{requestId}/decision")
    @PreAuthorize("hasRole('DONOR')")
    public RequestDtos.RequestResponse decide(@PathVariable long requestId, @AuthenticationPrincipal String email,
            @Valid @RequestBody RequestDtos.DecisionRequest body) {
        return requestService.decide(requestId, email, body.decision());
    }

    @PatchMapping("/requests/{requestId}/volunteer")
    @PreAuthorize("hasRole('DONOR')")
    public RequestDtos.RequestResponse assignVolunteer(@PathVariable long requestId,
            @AuthenticationPrincipal String email, @Valid @RequestBody RequestDtos.AssignVolunteerRequest body) {
        return requestService.assignVolunteer(requestId, email, body.volunteerId());
    }

    @PatchMapping("/requests/{requestId}/delivery-status")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public RequestDtos.RequestResponse delivery(@PathVariable long requestId, @AuthenticationPrincipal String email,
            @Valid @RequestBody RequestDtos.DeliveryStatusRequest body) {
        return requestService.updateDelivery(requestId, email, body.status());
    }
}
