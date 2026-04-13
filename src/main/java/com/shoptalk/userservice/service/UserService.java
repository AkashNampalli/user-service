package com.shoptalk.userservice.service;

import com.shoptalk.userservice.dto.RegisterRequest;
import com.shoptalk.userservice.dto.UserResponse;
import com.shoptalk.userservice.entity.User;
import com.shoptalk.userservice.exceptions.DuplicateEmailException;
import com.shoptalk.userservice.exceptions.UserNotFoundException;
import com.shoptalk.userservice.mapper.UserMapper;
import com.shoptalk.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }
@Transactional
    public UserResponse registerUser(RegisterRequest request){

        // Check if email already exists before trying to save.
        if(userRepository.findByEmail(request.getEmail()).isPresent()){
            throw new DuplicateEmailException(request.getEmail());
        }
        //convert RegisterRequest to User Entity
        User user = userMapper.toEntity(request);

        //Hash the password and set it back
       String hashedPassword =  passwordEncoder.encode(request.getPassword());
       user.setPassword(hashedPassword);

       //Save to DB
       User savedUser = userRepository.save(user);

       //convert saved user -> UserResponse and return
       return userMapper.toResponse(savedUser);
    }
@Transactional(readOnly = true)
    public UserResponse findByEmail(String email){

        return userRepository.findByEmail(email)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("Check your email my friend there is no user with that email "+email));
    }

@Transactional(readOnly = true)
    public UserResponse  findById(UUID id){
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("Bhayya there is no user with that Id "+id));
    }
}
