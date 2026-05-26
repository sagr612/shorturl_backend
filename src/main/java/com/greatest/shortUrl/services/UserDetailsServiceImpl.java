package com.greatest.shortUrl.services;


import com.greatest.shortUrl.entitiy.User;
import com.greatest.shortUrl.model.SignUpReq;
import com.greatest.shortUrl.model.SignUpResponse;
import com.greatest.shortUrl.repository.UserRepo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepo userRepo;

    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return UserDetailsImpl.build(user);
    }

    public User checkIfUserAlreadyExist(String email) {
        return userRepo.findByEmail(email).orElse(null);
    }

    public SignUpResponse signupUser(@Valid SignUpReq userInfo) {
        if (Objects.nonNull(checkIfUserAlreadyExist(userInfo.email()))) {
            return SignUpResponse.builder().isSignedUp(false).build();
        }
        User user = User.builder().name(userInfo.name()).email(userInfo.email()).password(passwordEncoder.encode(userInfo.password())).build();
        User savedUser = userRepo.save(user);
        return SignUpResponse.builder().isSignedUp(true).user(savedUser).build();
    }

    public Optional<User> findByEmail(String email) {
        return userRepo.findByEmail(email);
    }
}
