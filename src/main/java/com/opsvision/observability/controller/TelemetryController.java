package com.opsvision.observability.controller;

import com.opsvision.observability.dto.TelemetryResponse;
import com.opsvision.observability.mapper.TelemetryMapper;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.service.TelemetryCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/observability", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Observability", description = "Kubernetes and Prometheus telemetry collection")
public class TelemetryController {

    private final TelemetryCollectionService telemetryCollectionService;
    private final TelemetryMapper telemetryMapper;

    public TelemetryController(
            TelemetryCollectionService telemetryCollectionService,
            TelemetryMapper telemetryMapper
    ) {
        this.telemetryCollectionService = telemetryCollectionService;
        this.telemetryMapper = telemetryMapper;
    }

    @GetMapping("/telemetry")
    @Operation(summary = "Collect current Kubernetes and Prometheus telemetry snapshot")
    public TelemetryResponse collectTelemetry(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String workload
    ) {
        TelemetrySnapshot snapshot = telemetryCollectionService.collect(namespace, workload);
        return telemetryMapper.toResponse(snapshot);
    }
}
