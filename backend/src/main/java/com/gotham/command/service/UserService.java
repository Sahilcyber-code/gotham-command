package com.gotham.command.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.entity.AuthProvider;
import com.gotham.command.entity.User;
import com.gotham.command.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("User with email already exists: " + user.getEmail());
        }
        return userRepository.save(user);
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByEmailIgnoreCase(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    public Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public User updateUser(UUID id, User updatedFields) {
        User existing = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        if (updatedFields.getFullName() != null) {
            existing.setFullName(updatedFields.getFullName());
        }
        if (updatedFields.getDisplayName() != null) {
            existing.setDisplayName(updatedFields.getDisplayName());
        }
        if (updatedFields.getAvatarUrl() != null) {
            existing.setAvatarUrl(updatedFields.getAvatarUrl());
        }
        existing.setActive(updatedFields.isActive());

        return userRepository.save(existing);
    }

    @Transactional
    public User processGoogleOAuthUser(String providerId, String email, String fullName, String avatarUrl) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Google providerId must not be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google user email must not be null or blank");
        }

        // 1. Check if user with (GOOGLE, providerId) already exists
        Optional<User> byProvider = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId);
        if (byProvider.isPresent()) {
            User existing = byProvider.get();
            if (fullName != null && !fullName.isBlank()) {
                existing.setFullName(fullName);
                existing.setDisplayName(fullName);
            }
            if (avatarUrl != null) {
                existing.setAvatarUrl(avatarUrl);
            }
            return userRepository.save(existing);
        }

        // 2. Check if email exists in database
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            if (existing.getProvider() == AuthProvider.GOOGLE) {
                // Same provider, link providerId
                existing.setProviderId(providerId);
                if (avatarUrl != null) {
                    existing.setAvatarUrl(avatarUrl);
                }
                return userRepository.save(existing);
            } else {
                // Account collision with different provider (e.g. LOCAL)
                // Deterministic security policy: reject automatic takeover to protect against account takeover attacks
                throw new IllegalStateException(
                    "An account with email " + email + " already exists with provider " + existing.getProvider()
                    + ". Automatic account linking is not supported."
                );
            }
        }

        // 3. New user registration
        User newUser = new User();
        newUser.setProvider(AuthProvider.GOOGLE);
        newUser.setProviderId(providerId);
        newUser.setEmail(email.toLowerCase().trim());
        String name = (fullName != null && !fullName.isBlank()) ? fullName : email.split("@")[0];
        newUser.setFullName(name);
        newUser.setDisplayName(name);
        newUser.setUsername(email.split("@")[0]);
        newUser.setAvatarUrl(avatarUrl);
        newUser.setActive(true);
        newUser.setPasswordHash(null);

        return userRepository.save(newUser);
    }
}
