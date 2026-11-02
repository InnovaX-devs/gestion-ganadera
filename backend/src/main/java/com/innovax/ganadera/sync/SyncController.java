package com.innovax.ganadera.sync;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innovax.ganadera.sync.dto.SyncRequest;
import com.innovax.ganadera.sync.dto.SyncResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** {@code POST /api/sync}: push y pull de la app offline (#30). Ver docs/sync.md. */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService service;

    @PostMapping
    public SyncResponse sincronizar(@Valid @RequestBody SyncRequest request) {
        return service.sincronizar(request);
    }
}
