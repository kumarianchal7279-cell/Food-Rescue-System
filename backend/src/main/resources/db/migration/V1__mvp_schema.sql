CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    organization_name VARCHAR(160),
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('DONOR', 'NGO', 'VOLUNTEER', 'ADMIN')),
    INDEX idx_users_role (role)
);

CREATE TABLE donations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    food_name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL,
    description VARCHAR(1000),
    quantity_kg DECIMAL(8, 2) NOT NULL,
    preparation_time DATETIME NOT NULL,
    safe_consumption_deadline DATETIME NOT NULL,
    pickup_address VARCHAR(300) NOT NULL,
    image_url VARCHAR(1000),
    storage_conditions VARCHAR(500),
    allergens VARCHAR(500),
    handling_information VARCHAR(1000),
    food_safety_checklist_confirmed BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    volunteer_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_donations_donor FOREIGN KEY (donor_id) REFERENCES users (id),
    CONSTRAINT fk_donations_volunteer FOREIGN KEY (volunteer_id) REFERENCES users (id),
    CONSTRAINT chk_donations_status CHECK (status IN ('AVAILABLE', 'REQUESTED', 'ACCEPTED', 'PICKUP_ASSIGNED', 'COLLECTED', 'DELIVERED', 'CANCELLED', 'EXPIRED')),
    INDEX idx_donations_status_deadline (status, safe_consumption_deadline),
    INDEX idx_donations_donor_created (donor_id, created_at),
    INDEX idx_donations_category_status (category, status)
);

CREATE TABLE donation_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    donation_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    message VARCHAR(500),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_requests_donation FOREIGN KEY (donation_id) REFERENCES donations (id),
    CONSTRAINT fk_requests_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT chk_requests_status CHECK (status IN ('REQUESTED', 'ACCEPTED', 'REJECTED', 'COLLECTED', 'DELIVERED', 'CANCELLED')),
    INDEX idx_requests_receiver_created (receiver_id, requested_at),
    INDEX idx_requests_donation_status (donation_id, status)
);
