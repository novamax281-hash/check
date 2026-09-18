package com.example.placement.service;

import com.example.placement.dto.RoundRequest;
import com.example.placement.entity.Placement;
import com.example.placement.entity.Round;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.RoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoundServiceTest {

    private RoundRepository roundRepository;
    private PlacementRepository placementRepository;
    private RoundService roundService;

    @BeforeEach
    void setUp() {
        roundRepository = mock(RoundRepository.class);
        placementRepository = mock(PlacementRepository.class);
        roundService = new RoundService(roundRepository, placementRepository);
    }

    private RoundRequest request(Integer number, String name, String description) {
        RoundRequest request = new RoundRequest();
        request.setRoundNumber(number);
        request.setRoundName(name);
        request.setDescription(description);
        return request;
    }

    @Test
    void addRoundCreatesRoundForExistingJob() {
        Placement job = new Placement();
        job.setId("job1");
        when(placementRepository.existsById("job1")).thenReturn(true);
        when(roundRepository.existsByJobIdAndRoundNumber("job1", 3)).thenReturn(false);
        when(roundRepository.save(any(Round.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Round created = roundService.addRound("job1", request(3, "Technical Interview", "Panel discussion"));

        assertEquals("job1", created.getJobId());
        assertEquals(3, created.getRoundNumber());
        assertEquals("Technical Interview", created.getRoundName());
        assertEquals("Panel discussion", created.getDescription());
    }

    @Test
    void addRoundDefaultsBlankNameAndDescription() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        when(roundRepository.existsByJobIdAndRoundNumber("job1", 2)).thenReturn(false);
        when(roundRepository.save(any(Round.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Round created = roundService.addRound("job1", request(2, "   ", null));

        assertEquals("Round 2", created.getRoundName());
        assertNull(created.getDescription());
    }

    @Test
    void addRoundUnknownJobIsNotFound() {
        when(placementRepository.existsById("missing")).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> roundService.addRound("missing", request(1, "Aptitude", null)));

        assertEquals(404, exception.getStatusCode().value());
        verify(roundRepository, never()).save(any(Round.class));
    }

    @Test
    void addRoundDuplicateNumberIsConflict() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        when(roundRepository.existsByJobIdAndRoundNumber("job1", 1)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> roundService.addRound("job1", request(1, "Duplicate", null)));

        assertEquals(409, exception.getStatusCode().value());
        verify(roundRepository, never()).save(any(Round.class));
    }

    @Test
    void addRoundInvalidNumberIsBadRequest() {
        when(placementRepository.existsById("job1")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> roundService.addRound("job1", request(0, "Zero", null)));
        assertThrows(ResponseStatusException.class, () -> roundService.addRound("job1", request(-2, "Negative", null)));
        assertThrows(ResponseStatusException.class, () -> roundService.addRound("job1", request(null, "Missing", null)));
        verify(roundRepository, never()).save(any(Round.class));
    }

    @Test
    void listRoundsReturnsSortedRoundsForExistingJob() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        when(roundRepository.findByJobIdOrderByRoundNumberAsc("job1")).thenReturn(List.of(
                new Round("job1", 1, "Aptitude Test", null),
                new Round("job1", 2, "Coding Round", null)));

        List<Round> rounds = roundService.listRounds("job1");

        assertEquals(2, rounds.size());
        assertEquals(1, rounds.get(0).getRoundNumber());
        assertEquals(2, rounds.get(1).getRoundNumber());
    }

    @Test
    void listRoundsUnknownJobIsNotFound() {
        when(placementRepository.existsById("missing")).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> roundService.listRounds("missing"));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void updateRoundRenamesExistingRound() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        Round existing = new Round("job1", 2, "Coding Test", "Old description");
        when(roundRepository.findByJobIdAndRoundNumber("job1", 2)).thenReturn(Optional.of(existing));
        when(roundRepository.save(any(Round.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Round updated = roundService.updateRound("job1", 2, request(2, "Coding Challenge", "New description"));

        assertEquals("Coding Challenge", updated.getRoundName());
        assertEquals("New description", updated.getDescription());
        assertEquals(2, updated.getRoundNumber());
    }

    @Test
    void updateRoundRejectsNumberChange() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        when(roundRepository.findByJobIdAndRoundNumber("job1", 1))
                .thenReturn(Optional.of(new Round("job1", 1, "Aptitude", null)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> roundService.updateRound("job1", 1, request(5, "Renamed", null)));

        assertEquals(400, exception.getStatusCode().value());
        verify(roundRepository, never()).save(any(Round.class));
    }

    @Test
    void updateRoundMissingRoundIsNotFound() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        when(roundRepository.findByJobIdAndRoundNumber("job1", 9)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> roundService.updateRound("job1", 9, request(9, "Ghost", null)));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void deleteRoundRemovesExistingRound() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        Round existing = new Round("job1", 3, "HR Interview", null);
        when(roundRepository.findByJobIdAndRoundNumber("job1", 3)).thenReturn(Optional.of(existing));

        Round deleted = roundService.deleteRound("job1", 3);

        verify(roundRepository).delete(existing);
        assertEquals(3, deleted.getRoundNumber());
    }

    @Test
    void deleteRoundMissingRoundIsNotFound() {
        when(placementRepository.existsById("job1")).thenReturn(true);
        when(roundRepository.findByJobIdAndRoundNumber("job1", 7)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> roundService.deleteRound("job1", 7));

        assertEquals(404, exception.getStatusCode().value());
        verify(roundRepository, never()).delete(any(Round.class));
    }
}
