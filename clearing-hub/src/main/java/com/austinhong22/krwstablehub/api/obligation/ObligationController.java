package com.austinhong22.krwstablehub.api.obligation;

import com.austinhong22.krwstablehub.api.obligation.dto.ObligationRequest;
import com.austinhong22.krwstablehub.api.obligation.dto.ObligationResponse;
import com.austinhong22.krwstablehub.service.ObligationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/obligations")
@RequiredArgsConstructor
public class ObligationController {

    private final ObligationService obligationService;

    @PostMapping
    public ResponseEntity<ObligationResponse> receive(@Valid @RequestBody ObligationRequest request) {
        return ResponseEntity.accepted().body(obligationService.receive(request));
    }
}
