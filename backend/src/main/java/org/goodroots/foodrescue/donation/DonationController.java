package org.goodroots.foodrescue.donation;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/donations")
public class DonationController {
    private final DonationService donationService;

    public DonationController(DonationService donationService) { this.donationService = donationService; }

    @GetMapping
    @PreAuthorize("hasAnyRole('NGO', 'DONOR', 'VOLUNTEER', 'ADMIN')")
    public List<DonationDtos.DonationResponse> available(
            @RequestParam(required = false) String category, @RequestParam(required = false) String location) {
        return donationService.available(category, location);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('DONOR')")
    public List<DonationDtos.DonationResponse> mine(@AuthenticationPrincipal String email) {
        return donationService.mine(email);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('NGO', 'DONOR', 'VOLUNTEER', 'ADMIN')")
    public DonationDtos.DonationResponse get(@PathVariable long id, @AuthenticationPrincipal String email) {
        return donationService.get(id, email);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DONOR')")
    public DonationDtos.DonationResponse create(@AuthenticationPrincipal String email,
            @Valid @RequestBody DonationDtos.CreateDonationRequest request) {
        return donationService.create(email, request);
    }
}
