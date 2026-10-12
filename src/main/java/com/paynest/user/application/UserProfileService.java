package com.paynest.user.application;

import com.paynest.common.error.AuthenticationFailedException;
import com.paynest.user.api.UserResponse;
import com.paynest.user.persistence.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse currentProfile(long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(AuthenticationFailedException::new);
    }
}
