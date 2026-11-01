package org.goodroots.foodrescue.donation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.goodroots.foodrescue.user.User;

@Entity
@Table(name = "donations", indexes = {
    @Index(name = "idx_donations_status_deadline", columnList = "status, safe_consumption_deadline"),
    @Index(name = "idx_donations_donor_created", columnList = "donor_id, created_at"),
    @Index(name = "idx_donations_category_status", columnList = "category, status")
})
public class Donation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;
    @Column(name = "food_name", nullable = false, length = 120)
    private String foodName;
    @Column(nullable = false, length = 60)
    private String category;
    @Column(length = 1000)
    private String description;
    @Column(name = "quantity_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantityKg;
    @Column(name = "preparation_time", nullable = false)
    private LocalDateTime preparationTime;
    @Column(name = "safe_consumption_deadline", nullable = false)
    private LocalDateTime safeConsumptionDeadline;
    @Column(name = "pickup_address", nullable = false, length = 300)
    private String pickupAddress;
    @Column(name = "image_url", length = 1000)
    private String imageUrl;
    @Column(name = "storage_conditions", length = 500)
    private String storageConditions;
    @Column(length = 500)
    private String allergens;
    @Column(name = "handling_information", length = 1000)
    private String handlingInformation;
    @Column(name = "food_safety_checklist_confirmed", nullable = false)
    private boolean foodSafetyChecklistConfirmed;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DonationStatus status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "volunteer_id")
    private User volunteer;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Donation() {}

    public Donation(User donor, String foodName, String category, String description, BigDecimal quantityKg,
                    LocalDateTime preparationTime, LocalDateTime safeConsumptionDeadline, String pickupAddress,
                    String imageUrl, String storageConditions, String allergens, String handlingInformation,
                    boolean foodSafetyChecklistConfirmed) {
        this.donor = donor;
        this.foodName = foodName;
        this.category = category;
        this.description = description;
        this.quantityKg = quantityKg;
        this.preparationTime = preparationTime;
        this.safeConsumptionDeadline = safeConsumptionDeadline;
        this.pickupAddress = pickupAddress;
        this.imageUrl = imageUrl;
        this.storageConditions = storageConditions;
        this.allergens = allergens;
        this.handlingInformation = handlingInformation;
        this.foodSafetyChecklistConfirmed = foodSafetyChecklistConfirmed;
        this.status = DonationStatus.AVAILABLE;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void setStatus(DonationStatus status) { this.status = status; }
    public void setVolunteer(User volunteer) { this.volunteer = volunteer; }
    public Long getId() { return id; }
    public User getDonor() { return donor; }
    public String getFoodName() { return foodName; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public LocalDateTime getPreparationTime() { return preparationTime; }
    public LocalDateTime getSafeConsumptionDeadline() { return safeConsumptionDeadline; }
    public String getPickupAddress() { return pickupAddress; }
    public String getImageUrl() { return imageUrl; }
    public String getStorageConditions() { return storageConditions; }
    public String getAllergens() { return allergens; }
    public String getHandlingInformation() { return handlingInformation; }
    public boolean isFoodSafetyChecklistConfirmed() { return foodSafetyChecklistConfirmed; }
    public DonationStatus getStatus() { return status; }
    public User getVolunteer() { return volunteer; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
