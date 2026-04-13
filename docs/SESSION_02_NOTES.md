# ShopTalk — Session 02 Notes
**Date:** 13 April 2026
**Duration:** Full day session
**Topic:** DTOs, Exception Handling, Validation, @Transactional

---

## What we covered today

### 1. The problem with returning entities directly

Before this session, `UserController` was returning the raw `User` entity:

```java
// BEFORE — dangerous
public ResponseEntity<User> register(@RequestBody User user)
```

Three problems with this:

**Problem 1 — Over-exposure of sensitive fields**
Every database field came back in the response — including the BCrypt password hash.
Even a hash should never leave the server.

**Problem 2 — API tightly coupled to database schema**
Renaming a database column immediately breaks the API response shape.
Every frontend and mobile app that reads that field breaks instantly.

**Problem 3 — Different endpoints need different shapes**
Registration response only needs id + username.
Admin endpoint might need fields a regular user should never see.
One entity class cannot serve all these different shapes cleanly.

---

### 2. DTOs — Data Transfer Objects

A DTO is a plain Java class with no database annotations, no business logic.
Its only job is to carry data across a boundary.

```
Client sends JSON
       ↓
  RegisterRequest     ← what the client sends in
       ↓
  User entity         ← internal database representation only
       ↓
  UserResponse        ← what the client gets back (no password)
       ↓
Client receives JSON
```

The entity never crosses the API boundary. Only DTOs do.

---

### 3. Files created

#### `RegisterRequest.java`

```java
package com.shoptalk.userservice.dto;

import com.shoptalk.userservice.entity.UserRole;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotNull          // enum — use @NotNull, not @NotBlank
    private UserRole role;

    @Size(min = 10, max = 15)
    private String mobileNumber;
}
```

**Validation annotation rules:**
| Field type | Correct annotation |
|---|---|
| `String` | `@NotBlank` — checks not null AND not empty string |
| `Enum`, `Object` | `@NotNull` — checks not null only |
| `String` with format | `@Email`, `@Pattern` |
| `String` with length | `@Size(min=x, max=y)` |

**Common mistake fixed:** `@NotBlank` on an enum field causes:
`No validator could be found for constraint 'NotBlank' validating type 'UserRole'`
Always use `@NotNull` for non-String types.

---

#### `UserResponse.java`

```java
package com.shoptalk.userservice.dto;

import com.shoptalk.userservice.entity.UserRole;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private UserRole role;
    private String mobileNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // NO password field — intentional
}
```

---

#### `ErrorResponse.java`

```java
package com.shoptalk.userservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private int status;
    private String message;
    private LocalDateTime timestamp;
}
```

---

### 4. UserMapper — converting between entity and DTO

```java
package com.shoptalk.userservice.mapper;

import com.shoptalk.userservice.dto.RegisterRequest;
import com.shoptalk.userservice.dto.UserResponse;
import com.shoptalk.userservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request) {
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .mobileNumber(request.getMobileNumber())
                .role(request.getRole())
                .build();
    }

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .mobileNumber(user.getMobileNumber())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
        // password intentionally excluded
    }
}
```

**Key mistakes caught:**
- Methods were `static` — static methods cannot be injected by Spring, must be instance methods
- Missing `@Component` — Spring could not find or inject the mapper
- Field name inconsistency `userName` vs `username` — pick one convention and be consistent

---

### 5. Updated UserService — DTOs in, DTOs out

```java
package com.shoptalk.userservice.service;

import com.shoptalk.userservice.dto.RegisterRequest;
import com.shoptalk.userservice.dto.UserResponse;
import com.shoptalk.userservice.entity.User;
import com.shoptalk.userservice.exception.DuplicateEmailException;
import com.shoptalk.userservice.exception.UserNotFoundException;
import com.shoptalk.userservice.mapper.UserMapper;
import com.shoptalk.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse registerUser(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateEmailException(request.getEmail());
        }
        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() ->
                    new UserNotFoundException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toResponse)
                .orElseThrow(() ->
                    new UserNotFoundException("User not found with email: " + email));
    }
}
```

**Method signature change:**
```
BEFORE: public User registerUser(User user)
AFTER:  public UserResponse registerUser(RegisterRequest request)

BEFORE: public Optional<UserResponse> findById(UUID id)
AFTER:  public UserResponse findById(UUID id)
```

Service now returns `UserResponse` directly — controller no longer needs Optional handling.

---

### 6. Exception handling — @ControllerAdvice

#### Why we need it

Without exception handling, Spring dumps a raw 500 response with full stack trace:
- Exposes internal implementation details (table names, class names, SQL)
- Wrong status code — 500 means server crash, not client error
- Unreadable by frontend applications

#### Custom exceptions created

**`DuplicateEmailException.java`**
```java
package com.shoptalk.userservice.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("Email already exists: " + email);
    }
}
```

**`UserNotFoundException.java`**
```java
package com.shoptalk.userservice.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

**Why `RuntimeException`:**
Checked exceptions force try/catch everywhere — verbose and clutters code.
RuntimeException (unchecked) — Spring's `@ControllerAdvice` handles them automatically.

---

#### `GlobalExceptionHandler.java`

```java
package com.shoptalk.userservice.exception;

import com.shoptalk.userservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.CONFLICT.value())
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
          .getFieldErrors()
          .forEach(error ->
              errors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .message(errors.toString())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .message("Something went wrong. Please try again.")
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
```

**Handler order matters:**
Spring checks handlers from most specific to least specific.
`DuplicateEmailException` is caught before `Exception`.
Always put specific handlers above the generic `Exception` handler.

**Security note:**
Generic `Exception` handler returns `"Something went wrong"` — not `ex.getMessage()`.
This prevents internal details from leaking to clients in unexpected errors.

---

### 7. `@Valid` — enforcing validation annotations

Validation annotations on DTOs (`@NotBlank`, `@Email`) do nothing alone.
`@Valid` on the controller parameter tells Spring to enforce them:

```java
// WITHOUT @Valid — annotations ignored, empty username goes through
public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request)

// WITH @Valid — annotations enforced, throws MethodArgumentNotValidException
public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request)
```

When validation fails, Spring throws `MethodArgumentNotValidException`.
`GlobalExceptionHandler` catches it and returns a clean `400` with field-level errors:

```json
{
    "status": 400,
    "message": "{email=must be a well-formed email address, username=must not be blank}",
    "timestamp": "2026-04-13T16:01:58"
}
```

---

### 8. `@Transactional` — the all-or-nothing guarantee

#### The problem without it

If a method performs two database operations and crashes halfway:

```java
userRepository.save(user);           // operation 1 — succeeds, user saved
cartRepository.createCart(userId);   // operation 2 — crashes
```

Without `@Transactional`:
- User exists in database ✓
- Cart was never created ✗
- Database is in permanently broken state — partial update

#### How `@Transactional` fixes it

Wraps all operations in a single transaction — either all succeed or all roll back:

```java
@Transactional
public UserResponse registerUser(RegisterRequest request) {
    // operation 1
    // operation 2
    // if ANYTHING throws — both operations roll back
    // database returns to original state
}
```

#### How Spring implements it — proxies

Spring wraps your `@Transactional` class in a generated proxy at startup:

```
Your class:          Spring's proxy (what actually runs):
registerUser()   →   beginTransaction()
                     try {
                       yourActualCode()
                       commitTransaction()
                     } catch(Exception e) {
                       rollbackTransaction()
                       throw e
                     }
```

You never write begin/commit/rollback — the proxy does it invisibly.
This is why `@Transactional` only works on Spring-managed beans.

#### Usage in UserService

```java
@Transactional                      // write — full transaction protection
public UserResponse registerUser(RegisterRequest request)

@Transactional(readOnly = true)     // read — DB optimises for reads, no writes allowed
public UserResponse findById(UUID id)

@Transactional(readOnly = true)     // read
public UserResponse findByEmail(String email)
```

**Always use:**
```
import org.springframework.transaction.annotation.Transactional;
```
NOT `jakarta.transaction.Transactional` — Spring's version has more features including `readOnly`.

---

### 9. Updated UserController

```java
package com.shoptalk.userservice.controller;

import com.shoptalk.userservice.dto.RegisterRequest;
import com.shoptalk.userservice.dto.UserResponse;
import com.shoptalk.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody RegisterRequest request) {
        UserResponse savedUser = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.findByEmail(email));
    }
}
```

Controller is now very thin — only handles HTTP concerns.
All logic lives in the service layer.

---

### 10. Live test results

| Request | Expected | Actual |
|---|---|---|
| POST valid new user | 201 Created, no password in response | ✓ |
| POST duplicate email | 409 Conflict | ✓ |
| POST empty username | 400 Bad Request with field errors | ✓ |
| POST invalid email format | 400 Bad Request | ✓ |
| GET valid user by ID | 200 OK with UserResponse | ✓ |
| GET non-existent user by ID | 404 Not Found with message | ✓ |
| GET non-existent user by email | 404 Not Found with message | ✓ |

---

### 11. Final package structure after Session 02

```
com.shoptalk.userservice/
├── config/
│   └── SecurityConfig.java
├── controller/
│   └── UserController.java
├── dto/
│   ├── ErrorResponse.java
│   ├── RegisterRequest.java
│   └── UserResponse.java
├── entity/
│   ├── User.java
│   └── UserRole.java
├── exception/
│   ├── DuplicateEmailException.java
│   ├── GlobalExceptionHandler.java
│   └── UserNotFoundException.java
├── mapper/
│   └── UserMapper.java
├── repository/
│   └── UserRepository.java
├── service/
│   └── UserService.java
└── UserServiceApplication.java
```

---

### 12. Key concepts to remember

| Concept | One-line summary |
|---|---|
| DTO | Plain class that carries data — entity never crosses API boundary |
| `@Valid` | Enforces validation annotations on incoming request body |
| `@NotBlank` | String only — not null and not empty string |
| `@NotNull` | Any type — not null |
| `@ControllerAdvice` | Global exception handler — catches all exceptions before client sees them |
| `@ExceptionHandler` | Maps a specific exception class to a handler method |
| `RuntimeException` | Unchecked — Spring handles automatically, no try/catch clutter |
| `@Transactional` | All-or-nothing — multiple DB operations succeed together or roll back together |
| `readOnly = true` | Hints DB to optimise for reads, prevents accidental writes |
| Proxy pattern | Spring wraps @Transactional beans in generated proxy that handles begin/commit/rollback |

---

### 13. Issues encountered and resolved

| Issue | Cause | Fix |
|---|---|---|
| `No validator for NotBlank on UserRole` | `@NotBlank` only works on String | Changed to `@NotNull` on enum field |
| Validation annotations ignored | `@Valid` missing on controller parameter | Added `@Valid` before `@RequestBody` |
| Duplicate email returns 500 | No specific exception handler | Added `DuplicateEmailException` + handler in `GlobalExceptionHandler` |
| Static mapper methods | Static prevents Spring injection | Removed `static` keyword from mapper methods |

---

## What's coming in Session 03

```
├── Spring Security 6 deep dive
├── JWT — what it is, how it works, RS256 vs HS256
├── Auth Service — login, register, refresh token, logout
├── Token blacklisting with Redis
├── Connecting Auth Service to User Service
└── Testing the full auth flow end to end
```

---

*ShopTalk Learning Journal — Session 02 complete*
