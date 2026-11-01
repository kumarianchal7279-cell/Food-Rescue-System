package org.goodroots.foodrescue;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DonationWorkflowIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void protectedDonationApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/donations")).andExpect(status().isUnauthorized());
    }

    @Test
    void completesDonationRequestApprovalAndDeliveryWithRoleChecks() throws Exception {
        String donorToken = register("donor@example.org", "DONOR");
        String ngoToken = register("ngo@example.org", "NGO");
        String secondNgoToken = register("second-ngo@example.org", "NGO");
        String volunteerToken = register("volunteer@example.org", "VOLUNTEER");

        String prep = LocalDateTime.now().minusHours(1).withNano(0).toString();
        String deadline = LocalDateTime.now().plusDays(1).withNano(0).toString();
        String donationJson = """
            {
              "foodName":"Fresh vegetable trays",
              "category":"produce",
              "description":"Unopened seasonal vegetables",
              "quantityKg":12.5,
              "preparationTime":"%s",
              "safeConsumptionDeadline":"%s",
              "pickupAddress":"10 Community Street, Brooklyn",
              "storageConditions":"Refrigerated at 4 C",
              "allergens":"None declared",
              "handlingInformation":"Kept covered and chilled",
              "foodSafetyChecklistConfirmed":true
            }
            """.formatted(prep, deadline);

        mvc.perform(post("/api/donations")
                .header("Authorization", bearer(ngoToken))
                .contentType(MediaType.APPLICATION_JSON).content(donationJson))
            .andExpect(status().isForbidden());

        MvcResult createdDonation = mvc.perform(post("/api/donations")
                .header("Authorization", bearer(donorToken))
                .contentType(MediaType.APPLICATION_JSON).content(donationJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("AVAILABLE")))
            .andReturn();
        long donationId = read(createdDonation).path("id").asLong();

        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(donorToken)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(secondNgoToken)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(volunteerToken)))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/donations").header("Authorization", bearer(ngoToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].foodName", is("Fresh vegetable trays")));

        String requestBody = "{\"message\":\"We can collect this today.\"}";
        MvcResult createdRequest = mvc.perform(post("/api/donations/{id}/requests", donationId)
                .header("Authorization", bearer(ngoToken))
                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("REQUESTED")))
            .andReturn();
        long requestId = read(createdRequest).path("id").asLong();

        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(ngoToken)))
            .andExpect(status().isOk());
        mvc.perform(post("/api/donations/{id}/requests", donationId)
                .header("Authorization", bearer(secondNgoToken))
                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isConflict());

        mvc.perform(patch("/api/requests/{id}/decision", requestId)
                .header("Authorization", bearer(donorToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ACCEPTED")));

        JsonNode volunteer = read(mvc.perform(get("/api/users/volunteers")
                .header("Authorization", bearer(donorToken))).andExpect(status().isOk()).andReturn())
            .get(0);
        mvc.perform(patch("/api/requests/{id}/volunteer", requestId)
                .header("Authorization", bearer(donorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"volunteerId\":" + volunteer.path("id").asLong() + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.donation.status", is("PICKUP_ASSIGNED")));

        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(volunteerToken)))
            .andExpect(status().isOk());

        mvc.perform(patch("/api/requests/{id}/delivery-status", requestId)
                .header("Authorization", bearer(volunteerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COLLECTED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.donation.status", is("COLLECTED")));

        mvc.perform(patch("/api/requests/{id}/delivery-status", requestId)
                .header("Authorization", bearer(volunteerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.donation.status", is("DELIVERED")));

        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(ngoToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("DELIVERED")));
    }

    @Test
    void rejectsDonationWithoutSafetyChecklistConfirmation() throws Exception {
        String donorToken = register("unsafe@example.org", "DONOR");
        String prep = LocalDateTime.now().minusHours(1).withNano(0).toString();
        String deadline = LocalDateTime.now().plusDays(1).withNano(0).toString();
        String donationJson = """
            {
              "foodName":"Unconfirmed food",
              "category":"prepared",
              "quantityKg":2,
              "preparationTime":"%s",
              "safeConsumptionDeadline":"%s",
              "pickupAddress":"Brooklyn",
              "foodSafetyChecklistConfirmed":false
            }
            """.formatted(prep, deadline);
        mvc.perform(post("/api/donations")
                .header("Authorization", bearer(donorToken))
                .contentType(MediaType.APPLICATION_JSON).content(donationJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("foodSafetyChecklistConfirmed")));
    }

    @Test
    void revokedReceiverCannotReadDonationDetailsAfterRejection() throws Exception {
        String donorToken = register("privacy-donor@example.org", "DONOR");
        String firstNgoToken = register("privacy-first-ngo@example.org", "NGO");
        String nextNgoToken = register("privacy-next-ngo@example.org", "NGO");
        String prep = LocalDateTime.now().minusHours(1).withNano(0).toString();
        String deadline = LocalDateTime.now().plusDays(1).withNano(0).toString();
        String donationJson = """
            {
              "foodName":"Privacy test donation",
              "category":"produce",
              "quantityKg":2,
              "preparationTime":"%s",
              "safeConsumptionDeadline":"%s",
              "pickupAddress":"Private Pickup Address",
              "foodSafetyChecklistConfirmed":true
            }
            """.formatted(prep, deadline);
        MvcResult createdDonation = mvc.perform(post("/api/donations")
                .header("Authorization", bearer(donorToken))
                .contentType(MediaType.APPLICATION_JSON).content(donationJson))
            .andExpect(status().isCreated()).andReturn();
        long donationId = read(createdDonation).path("id").asLong();
        String requestJson = "{\"message\":\"Please consider our request.\"}";
        MvcResult firstRequest = mvc.perform(post("/api/donations/{id}/requests", donationId)
                .header("Authorization", bearer(firstNgoToken))
                .contentType(MediaType.APPLICATION_JSON).content(requestJson))
            .andExpect(status().isCreated()).andReturn();
        long firstRequestId = read(firstRequest).path("id").asLong();

        mvc.perform(patch("/api/requests/{id}/decision", firstRequestId)
                .header("Authorization", bearer(donorToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(firstNgoToken)))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/donations/{id}/requests", donationId)
                .header("Authorization", bearer(nextNgoToken))
                .contentType(MediaType.APPLICATION_JSON).content(requestJson))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/donations/{id}", donationId).header("Authorization", bearer(nextNgoToken)))
            .andExpect(status().isOk());
    }

    @Test
    void signsInWithHashedPasswordAndRejectsPublicAdminRegistration() throws Exception {
        register("login@example.org", "NGO");
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@example.org\",\"password\":\"SecurePass123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role", is("NGO")))
            .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@example.org\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Admin\",\"email\":\"admin@example.org\",\"password\":\"SecurePass123!\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());
    }

    private String register(String email, String role) throws Exception {
        String body = """
            {"fullName":"Test %s","organizationName":"Test organization","email":"%s","password":"SecurePass123!","role":"%s"}
            """.formatted(role, email, role);
        MvcResult result = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        return read(result).path("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
