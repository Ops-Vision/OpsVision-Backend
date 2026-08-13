package com.opsvision.deployment.repository;

import com.opsvision.deployment.entity.ProjectRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepositoryRepository extends JpaRepository<ProjectRepository, Long> {

    Optional<ProjectRepository> findByOwnerAndName(String owner, String name);

    Optional<ProjectRepository> findByFullName(String fullName);

    boolean existsByOwnerAndName(String owner, String name);
}
