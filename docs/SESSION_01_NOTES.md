# ShopTalk — Session 01 Notes
**Date:** 08 April 2026  
**Duration:** Full day session  
**Topic:** Spring Boot fundamentals + User Service — Phase 0 & Phase 1  

---

## What we covered today

### 1. The evolution of Java web development

#### Era 1 — Raw Servlets (2000–2003)
- To expose a single HTTP endpoint you had to write a `HttpServlet` class manually
- Every endpoint required XML registration in `web.xml`
- Objects and dependencies were created manually using `new`
- Had to install and configure a separate Tomcat/JBoss server
- Deploying meant building a WAR file and dropping it into the server

#### Era 2 — Spring Framework (2004–2013)
- Introduced the IoC Container — Spring managed object creation
- Removed raw Servlet boilerplate
- Still required hundreds of lines of XML configuration (`applicationContext.xml`)
- Every bean, datasource, transaction manager had to be manually declared

#### Era 3 — Spring Boot (2014 → now)
- Convention over configuration — Spring Boot makes intelligent defaults
- Embedded Tomcat bundled inside the JAR — run with `java -jar myapp.jar`
- Auto-configuration reads your classpath and activates relevant configs automatically
- Starter dependencies pull in all compatible versions in one line
- Production-ready out of the box — Actuator, logging, profiles

---

### 2. How `@SpringBootApplication` works internally

`@SpringBootApplication` is a shortcut for three annotations combined:

```java
@SpringBootConfiguration  // marks the class as a config class
@EnableAutoConfiguration  // reads classpath → activates relevant Spring configs
@ComponentScan            // scans current package + sub-packages for beans
```

**Key insight:** `@EnableAutoConfiguration` does not scan your code — it activates Spring Boot's ~150 pre-written configuration classes based on which JARs are on your classpath.  
Example: add `spring-boot-starter-data-jpa` → JPA auto-configuration activates automatically.

---

### 3. Inversion of Control (IoC) and Dependency Injection (DI)

#### The old approach — 3 problems

**Problem 1 — Wasteful object creation**  
Every request created new instances of all dependencies. 10,000 orders/minute = 30,000 objects created and thrown away.

**Problem 2 — Tight coupling**  
`OrderService` calling `new OrderRepository()` means `OrderService` must know how to build `OrderRepository` — including its constructor arguments, database config, connection pool setup. A change to `OrderRepository` forces changes in `OrderService`.

**Problem 3 — Untestable code**  
Because `OrderService` creates its own dependencies internally, you cannot swap them for fakes in unit tests. Every test required a real database, real Kafka — test suites took 20–30 minutes to run.

#### The IoC solution

Instead of a class creating its dependencies — it declares what it needs and receives them from outside.

```java
// Old way — OrderService controls creation (bad)
public OrderService() {
    this.orderRepository = new OrderRepository(); // tight coupling
}

// IoC way — dependencies provided from outside (good)
public OrderService(OrderRepository repo, KafkaProducer kafka) {
    this.orderRepository = repo;   // received, not created
    this.kafkaProducer   = kafka;
}
```

**IoC** = the philosophy — don't create your dependencies, declare them.  
**DI** = the mechanism — passing dependencies in through constructor/setter/field.  
**Spring IoC Container** = the engine — reads annotations at startup, builds the entire dependency tree bottom-up, wires everything together, stores every bean as a singleton.

#### The singleton benefit
Spring creates each bean **once** at startup and reuses that same instance for every request. `OrderService` is instantiated once regardless of how many orders come in.

---

### 4. Spring annotations — `@Component` vs `@Service` vs `@Repository`

All three register a class as a bean in the IoC container. The difference:

| Annotation | Layer | Extra behaviour |
|---|---|---|
| `@Component` | Generic utility/infrastructure | None |
| `@Service` | Business logic layer | None — semantic only |
| `@Repository` | Database access layer | **Exception translation** |

**Exception translation** (the hidden technical difference):  
`@Repository` tells Spring to intercept database-specific exceptions (`PSQLException`, `MySQLException`) and translate them into Spring's unified `DataIntegrityViolationException`. This means your service layer never depends on a specific database vendor — swap PostgreSQL for MySQL and nothing breaks.

---

### 5. The 3-layer architecture

Every professional Spring Boot microservice follows this structure:

```
HTTP Request
     ↓
Controller   (@RestController)  — receives request, sends response, no business logic
     ↓
Service      (@Service)         — all business logic lives here
     ↓
Repository   (@Repository)      — database access only
     ↓
PostgreSQL
```

---

### 6. What we built — User Service

#### Project setup
- **Spring Initializr:** `https://start.spring.io`
- **Group:** `com.shoptalk`
- **Artifact:** `user-service`
- **Package:** `com.shoptalk.userservice`
- **Java:** 21
- **Spring Boot:** 3.5.13
- **Packaging:** Jar
- **Config:** YAML

**Dependencies added:**
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `postgresql`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-devtools`
- `lombok` (version 1.18.36)

---

#### Package structure created

```
com.shoptalk.userservice/
├── config/
│   └── SecurityConfig.java
├── controller/
│   └── UserController.java
├── entity/
│   ├── User.java
│   └── UserRole.java
├── repository/
│   └── UserRepository.java
├── service/
│   └── UserService.java
└── UserServiceApplication.java
```

---

#### `UserRole.java` — enum

```java
public enum UserRole {
    BUYER,
    SELLER
}
```

**Why enum over String:** Invalid values are impossible at compile time. `"BUYERR"` cannot be stored.  
**Why `@Enumerated(EnumType.STRING)`:** Stores `"BUYER"` / `"SELLER"` as text. Never use `ORDINAL` — adding a new enum value reorders positions and silently corrupts existing data.

---

#### `User.java` — JPA entity

```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(length = 15)
    private String mobileNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

**Annotations explained:**
- `@Entity` — tells JPA this class maps to a database table
- `@Table(name = "users")` — explicit name to avoid conflict with PostgreSQL reserved word `user`
- `@Id` — marks the primary key field
- `@GeneratedValue(strategy = GenerationType.UUID)` — auto-generates UUID on save
- `@Column` — configures column constraints: `nullable`, `unique`, `length`, `updatable`
- `@Enumerated(EnumType.STRING)` — stores enum name as text
- `@PrePersist` — lifecycle hook — runs automatically before INSERT
- `@PreUpdate` — lifecycle hook — runs automatically before UPDATE
- `@Data` — Lombok: generates getters, setters, toString, equals, hashCode
- `@Builder` — Lombok: enables clean object construction pattern
- `@NoArgsConstructor` — Lombok: required by JPA
- `@AllArgsConstructor` — Lombok: all-fields constructor

**Why UUID over auto-increment?**  
In microservices, multiple services generate IDs independently. Auto-increment causes collisions (User Service id=1, Order Service id=1). UUID is globally unique across every service and database.

**Why `mobileNumber` is String not number:**  
Phone numbers have `+` prefix, country codes, and leading zeros — all lost in numeric types.

---

#### `UserRepository.java`

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
```

**Key concepts:**
- `JpaRepository<User, UUID>` — first type = entity, second type = primary key type
- Spring Data JPA generates full implementation at startup — you write zero SQL
- Built-in methods: `save()`, `findById()`, `findAll()`, `deleteById()`, `count()`, `existsById()`
- **Derived queries** — Spring reads method names and generates SQL automatically:
  - `findByEmail` → `SELECT * FROM users WHERE email = ?`
  - `findByUsernameAndRole` → `SELECT * FROM users WHERE username = ? AND role = ?`
- `Optional<User>` — safer than returning null, forces callers to handle missing data

---

#### `UserService.java`

```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }
}
```

**Key mistakes caught and fixed:**
1. `passwordEncoder.encode()` returns the hash — must capture and set back: `user.setPassword(hash)`
2. `userRepository.save()` returns the saved entity with UUID filled in — always return the result of `save()`, not the original object passed in (original has `id = null`)

---

#### `SecurityConfig.java`

```java
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
```

**Why `@Bean` not `@Component`:**  
`@Component` can only be placed on classes you wrote. `PasswordEncoder` is a Spring Security interface — you cannot modify it. `@Bean` inside `@Configuration` is how you register third-party classes into the IoC container.

**Why BCrypt strength 12:**  
BCrypt applies hashing rounds = 2^strength. Strength 12 = 4096 rounds. High enough to be slow for attackers, fast enough for production. Industry standard.

**Why `SecurityFilterChain`:**  
Spring Security 6 removed `WebSecurityConfigurerAdapter`. Security is now configured via a `SecurityFilterChain` bean — cleaner composition-based approach. We permit all requests for now — JWT security added when Auth Service is built.

---

#### `UserController.java`

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody User user) {
        User savedUser = userService.registerUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable UUID id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return userService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

**Key concepts:**
- `@RestController` = `@Controller` + `@ResponseBody` — auto-converts return values to JSON
- `@RequestMapping("/api/users")` — base path prefix for all endpoints in this controller
- `@RequestBody` — deserializes incoming JSON to Java object
- `@PathVariable` — extracts value from URL path: `/api/users/{id}`
- `ResponseEntity` — controls both the response body AND the HTTP status code
- `.map(ResponseEntity::ok).orElse(notFound())` — clean Optional handling pattern

**HTTP status codes used:**
- `201 Created` — POST that creates a new resource
- `200 OK` — successful GET
- `404 Not Found` — resource does not exist

**Key mistakes caught:**
1. Both GET endpoints had path `/api/users/{variable}` — Spring cannot differentiate. Fix: email path changed to `/api/users/email/{email}`
2. Return type was `User` not `ResponseEntity<User>` — status codes were being thrown away
3. `register()` was not calling `userService.registerUser()` — echoing raw input back without saving

---

#### `application.yml`

```yaml
spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://localhost:5432/userservice_db
    username: shoptalk
    password: shoptalk123
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true

server:
  port: 8081
```

**`ddl-auto` options explained:**
- `create-drop` — creates tables on startup, drops on shutdown (development only)
- `update` — updates schema without dropping (staging)
- `validate` — only validates, no changes (pre-production)
- `none` — do nothing, use migration tools (production)

**Port 8081** — API Gateway will occupy 8080. Each microservice gets its own port.

---

### 7. Infrastructure setup

#### Docker — running PostgreSQL locally

```bash
docker run --name shoptalk-postgres \
  -e POSTGRES_DB=userservice_db \
  -e POSTGRES_USER=shoptalk \
  -e POSTGRES_PASSWORD=shoptalk123 \
  -p 5432:5432 \
  -d postgres:16
```

- `-e` = environment variable (configures the container)
- `-p 5432:5432` = hostPort:containerPort — exposes PostgreSQL to your machine
- `-d` = detached mode — runs in background
- `postgres:16` = official PostgreSQL image version 16

**Useful commands:**
```bash
docker ps                    # list running containers
docker start shoptalk-postgres  # start stopped container
docker stop shoptalk-postgres   # stop container
docker exec -it shoptalk-postgres psql -U shoptalk -d userservice_db  # open psql
```

---

### 8. Live test results — Postman

**POST /api/users/register**
```json
Request:
{
    "username": "Akash Nampalli",
    "email": "akashnp1925@gmail.com",
    "password": "Advaith@29",
    "role": "BUYER",
    "mobileNumber": "9666381576"
}

Response: 201 Created
{
    "id": "9ce9efa5-2c96-4848-b918-f04aefd64cc3",
    "username": "Akash Nampalli",
    "email": "akashnp1925@gmail.com",
    "password": "$2a$12$WVnUTReOeg59Hxpp9p1V4OZrcLhE4ODIRWl6Kz7JZVXF9MEXZiO7C",
    "role": "BUYER",
    "mobileNumber": "9666381576",
    "createdAt": "2026-04-08T14:20:59.827041",
    "updatedAt": "2026-04-08T14:20:59.827057"
}
```

**Confirmed in PostgreSQL:**
```sql
SELECT id, username, email, role, created_at FROM users;
-- 9ce9efa5 | Akash Nampalli | akashnp1925@gmail.com | BUYER | 2026-04-08 14:20:59
```

---

### 9. Key concepts to remember

| Concept | One-line summary |
|---|---|
| IoC | Your class declares what it needs — container provides it |
| DI | The act of passing dependencies through constructor/setter |
| `@Bean` | Register third-party classes into the IoC container |
| `@Entity` | This Java class maps to a database table |
| `@GeneratedValue(UUID)` | Auto-generate UUID primary key on save |
| `@PrePersist` | Runs automatically before first INSERT |
| `@Enumerated(STRING)` | Store enum as text — never use ORDINAL |
| `Optional<T>` | Safer than null — forces caller to handle missing data |
| `save()` return value | Always return result of `save()` — contains the generated ID |
| `ResponseEntity` | Controls both body and HTTP status code of response |
| Derived queries | Spring reads method name → generates SQL automatically |

---

### 10. Issues encountered and resolved

| Issue | Cause | Fix |
|---|---|---|
| `Lombok 1.18.44 not found` | Spring Boot 3.5.13 referenced unreleased Lombok version | Pin `<version>1.18.36</version>` explicitly |
| `TypeTag :: UNKNOWN` | JDK 26 incompatible with Lombok 1.18.36 | Installed Java 21 (Amazon Corretto), set JAVA_HOME |
| `Connection to localhost:5432 refused` | Docker Desktop not running | Opened Docker Desktop, ran `docker start shoptalk-postgres` |
| `No beans of PasswordEncoder type found` | `PasswordEncoder` is third-party interface | Created `@Bean` method in `SecurityConfig` |
| Duplicate path conflict `/{id}` and `/{email}` | Both had same path pattern | Changed email path to `/email/{email}` |

---

## What's coming in Session 02

```
├── DTOs (Data Transfer Objects) — never expose entity directly in API response
├── Global exception handling — @ControllerAdvice, custom error responses
├── Input validation — @Valid, @NotBlank, @Email on request bodies
├── @Transactional — what it does, when to use it, what happens without it
└── UserService — complete with all edge cases (duplicate email, user not found)
```

---

## Files created this session

```
user-service/
├── src/main/java/com/shoptalk/userservice/
│   ├── config/SecurityConfig.java
│   ├── controller/UserController.java
│   ├── entity/User.java
│   ├── entity/UserRole.java
│   ├── repository/UserRepository.java
│   ├── service/UserService.java
│   └── UserServiceApplication.java
└── src/main/resources/
    └── application.yml
```

---

*ShopTalk Learning Journal — Session 01 complete*
