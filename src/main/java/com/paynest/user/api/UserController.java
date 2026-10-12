package com.paynest.user.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.user.application.UserProfileService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public UserResponse currentProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return userProfileService.currentProfile(user.id());
    }
}
