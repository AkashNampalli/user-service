package com.shoptalk.userservice.service;

import com.shoptalk.userservice.entity.User;
import com.shoptalk.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(User user){
       String hashedPassword =  passwordEncoder.encode(user.getPassword());
       user.setPassword(hashedPassword);
       user =  userRepository.save(user);
       return user;
    }

    public Optional<User> findByEmail(String email){
        Optional<User> user = userRepository.findByEmail(email);
        return user;
    }


    public Optional<User>  findById(UUID id){
        Optional<User> user = userRepository.findById(id);
        return user;
    }
}
