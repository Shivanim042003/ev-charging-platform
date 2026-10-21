package com.evcharging.platform.reservation;

import com.evcharging.platform.reservation.dto.CreateReservationRequest;
import com.evcharging.platform.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Every endpoint acts on behalf of the authenticated user; the owner always comes from the token. */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request, Principal principal) {
        // Any userRef in the body is ignored: a user can only book for themselves.
        CreateReservationRequest owned = new CreateReservationRequest(
                request.connectorId(), principal.getName(), request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(owned));
    }

    @PatchMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable UUID id, Principal principal) {
        // Ownership is enforced inside the service query; another user's reservation looks like a 404.
        return reservationService.cancel(id, principal.getName());
    }

    @GetMapping
    public List<ReservationResponse> mine(Principal principal) {
        return reservationService.listForUser(principal.getName());
    }
}