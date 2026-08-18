package com.opsvision.incident.repository;

import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.model.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @EntityGraph(attributePaths = {"timelineEntries", "deployment"})
    @Query("select i from Incident i where i.id = :id")
    Optional<Incident> findByIdWithTimeline(@Param("id") Long id);

    Page<Incident> findAllByOrderByDetectedAtDesc(Pageable pageable);

    List<Incident> findByDeploymentIdOrderByDetectedAtDesc(Long deploymentId);

    @Query("""
            select i from Incident i
            where i.status in :openStatuses
              and (:namespace is null or i.namespace = :namespace)
              and (:workload is null or i.workloadName = :workload)
              and (:deploymentId is null or i.deployment.id = :deploymentId)
            order by i.detectedAt desc
            """)
    List<Incident> findOpenMatching(
            @Param("openStatuses") List<IncidentStatus> openStatuses,
            @Param("namespace") String namespace,
            @Param("workload") String workload,
            @Param("deploymentId") Long deploymentId
    );
}
