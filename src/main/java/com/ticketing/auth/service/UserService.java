package com.ticketing.auth.service;

import com.ticketing.auth.entity.User;
import com.ticketing.auth.repository.UserRepository;
import com.ticketing.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUserEntityById(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id
                        ));
    }
}