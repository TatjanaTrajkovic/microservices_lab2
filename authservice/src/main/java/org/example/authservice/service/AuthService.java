package org.example.authservice.service;

import org.example.authservice.dto.LoginRequest;
import org.example.authservice.dto.LoginResponse;
import org.example.authservice.dto.RegisterRequest;
import org.example.authservice.model.UserCredentials;
import org.example.authservice.repository.UserCredentialsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserCredentialsRepository repo;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();//bcrypt

    public AuthService(UserCredentialsRepository repo, JwtService jwtService) {
        this.repo = repo;
        this.jwtService = jwtService;
    }

    //Vid registrering den hashar lösenordet
    public void register(RegisterRequest req) {
        if (repo.existsById(req.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "UserId already registered");
        }
        if (repo.findByUsername(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        repo.save(new UserCredentials(req.userId(), req.username(), encoder.encode(req.password())));
    }

    public LoginResponse login(LoginRequest req) {
        var creds = repo.findByUsername(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!encoder.matches(req.password(), creds.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        return new LoginResponse(jwtService.generateToken(creds.getUserId(), creds.getUsername()), creds.getUserId());
    }
}