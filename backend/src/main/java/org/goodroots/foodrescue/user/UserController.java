package org.goodroots.foodrescue.user;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository users) { this.users = users; }

    @GetMapping("/volunteers")
    @PreAuthorize("hasAnyRole('DONOR', 'ADMIN')")
    public List<UserDtos.VolunteerResponse> volunteers() {
        return users.findAllByRole(UserRole.VOLUNTEER, Sort.by(Sort.Direction.ASC, "fullName")).stream()
            .map(UserDtos.VolunteerResponse::from).toList();
    }
}
