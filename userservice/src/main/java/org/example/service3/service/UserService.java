package org.example.service3.service;

import org.example.service3.dto.CreateUserRequest;
import org.example.service3.dto.UserResponse;
import org.example.service3.model.User;
import org.example.service3.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    public UserResponse create(CreateUserRequest req) {
        if (repo.findByUsername(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        return toDto(repo.save(user));
    }

    public UserResponse getById(String id) {
        return repo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public List<UserResponse> getAll() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    public UserResponse update(String id, CreateUserRequest req) {
        User user = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setUsername(req.username());
        user.setEmail(req.email());
        return toDto(repo.save(user));
    }

    public void delete(String id) {
        repo.deleteById(id);
    }

    private UserResponse toDto(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail());
    }
}