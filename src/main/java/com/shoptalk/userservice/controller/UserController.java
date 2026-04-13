package com.shoptalk.userservice.controller;

import com.shoptalk.userservice.dto.RegisterRequest;
import com.shoptalk.userservice.dto.UserResponse;
import com.shoptalk.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request){
        UserResponse savedUser = userService.registerUser(request); // hash + save
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser); // return 201 created status code.
    }
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse>  getUser(@PathVariable UUID id){


        // 200 with user body
        // 404 empty body
        return ResponseEntity.ok(userService.findById(id));

    }
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponse>  getUserByEmail(@PathVariable String email){
        // 200 with user body
        // 404 empty body
        return ResponseEntity.ok(userService.findByEmail(email));
    }


}
