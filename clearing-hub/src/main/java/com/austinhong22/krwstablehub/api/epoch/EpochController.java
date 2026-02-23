package com.austinhong22.krwstablehub.api.epoch;

import com.austinhong22.krwstablehub.api.epoch.dto.EpochDetailResponse;
import com.austinhong22.krwstablehub.service.EpochQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/epochs")
@RequiredArgsConstructor
public class EpochController {

    private final EpochQueryService epochQueryService;

    @GetMapping("/{epochId}")
    public ResponseEntity<EpochDetailResponse> getEpoch(@PathVariable Long epochId) {
        return ResponseEntity.ok(epochQueryService.getEpoch(epochId));
    }
}
