package org.example.service2.repository;

import org.example.service2.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findAllByOrderByTimestampDesc();
}