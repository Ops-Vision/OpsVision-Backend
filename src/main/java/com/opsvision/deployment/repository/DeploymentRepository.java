package com.opsvision.deployment.repository;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.model.DeploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Optional<Deployment> findByRepositoryIdAndCommitShaAndEnvironment(
            Long repositoryId,
            String commitSha,
            String environment
    );

    List<Deployment> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    List<Deployment> findByStatusOrderByCreatedAtDesc(DeploymentStatus status);

    Page<Deployment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select d from Deployment d
            join fetch d.repository
            where d.id = :id
            """)
    Optional<Deployment> findByIdWithRepository(@Param("id") Long id);

    @Query("""
            select d from Deployment d
            left join fetch d.evidenceItems
            where d.id = :id
            """)
    Optional<Deployment> findByIdWithEvidence(@Param("id") Long id);

    @Query("""
            select distinct d from Deployment d
            left join fetch d.findings
            where d.id = :id
            """)
    Optional<Deployment> findByIdWithFindings(@Param("id") Long id);
}
