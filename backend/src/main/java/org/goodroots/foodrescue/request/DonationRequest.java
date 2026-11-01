package org.goodroots.foodrescue.request;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.goodroots.foodrescue.donation.Donation;
import org.goodroots.foodrescue.user.User;

@Entity
@Table(name = "donation_requests", indexes = {
    @Index(name = "idx_requests_receiver_created", columnList = "receiver_id, requested_at"),
    @Index(name = "idx_requests_donation_status", columnList = "donation_id, status")
})
public class DonationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donation_id", nullable = false)
    private Donation donation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status;
    @Column(length = 500)
    private String message;
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DonationRequest() {}

    public DonationRequest(Donation donation, User receiver, String message) {
        this.donation = donation;
        this.receiver = receiver;
        this.message = message;
        this.status = RequestStatus.REQUESTED;
    }

    @PrePersist
    void onCreate() {
        requestedAt = LocalDateTime.now();
        updatedAt = requestedAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public void setStatus(RequestStatus status) { this.status = status; }
    public Long getId() { return id; }
    public Donation getDonation() { return donation; }
    public User getReceiver() { return receiver; }
    public RequestStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
}
