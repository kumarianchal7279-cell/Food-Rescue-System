package org.goodroots.foodrescue.user;

public final class UserDtos {
    private UserDtos() {}
    public record VolunteerResponse(long id, String fullName, String organizationName) {
        public static VolunteerResponse from(User user) {
            return new VolunteerResponse(user.getId(), user.getFullName(), user.getOrganizationName());
        }
    }
}
