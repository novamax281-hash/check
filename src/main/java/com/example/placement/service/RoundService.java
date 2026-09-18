package com.example.placement.service;

import com.example.placement.dto.RoundRequest;
import com.example.placement.entity.Round;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.RoundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoundService {

    private final RoundRepository roundRepository;
    private final PlacementRepository placementRepository;

    public RoundService(RoundRepository roundRepository, PlacementRepository placementRepository) {
        this.roundRepository = roundRepository;
        this.placementRepository = placementRepository;
    }

    public Round addRound(String jobId, RoundRequest request) {
        requireJob(jobId);
        Integer number = request == null ? null : request.getRoundNumber();
        if (number == null || number < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Round number must be a positive integer");
        }
        if (roundRepository.existsByJobIdAndRoundNumber(jobId, number)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round " + number + " already exists for this job");
        }
        String name = request.getRoundName() == null || request.getRoundName().isBlank()
                ? "Round " + number : request.getRoundName().trim();
        String description = request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim();
        return roundRepository.save(new Round(jobId, number, name, description));
    }

    public List<Round> listRounds(String jobId) {
        requireJob(jobId);
        return findByJob(jobId);
    }

    public Round updateRound(String jobId, int roundNumber, RoundRequest request) {
        requireJob(jobId);
        Round existing = requireRound(jobId, roundNumber);
        Integer requestedNumber = request == null ? null : request.getRoundNumber();
        if (requestedNumber != null && requestedNumber.intValue() != roundNumber) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Round number cannot be changed");
        }
        if (request != null && request.getRoundName() != null) {
            existing.setRoundName(request.getRoundName().isBlank()
                    ? "Round " + roundNumber : request.getRoundName().trim());
        }
        if (request != null && request.getDescription() != null) {
            existing.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
        }
        return roundRepository.save(existing);
    }

    public Round deleteRound(String jobId, int roundNumber) {
        requireJob(jobId);
        Round existing = requireRound(jobId, roundNumber);
        roundRepository.delete(existing);
        return existing;
    }

    private Round requireRound(String jobId, int roundNumber) {
        return roundRepository.findByJobIdAndRoundNumber(jobId, roundNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Round " + roundNumber + " does not exist for this job"));
    }

    public List<Round> findByJob(String jobId) {
        return jobId == null ? List.of() : roundRepository.findByJobIdOrderByRoundNumberAsc(jobId);
    }

    public Map<String, List<Round>> findByJobs(java.util.Collection<String> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return Map.of();
        }
        return roundRepository.findByJobIdInOrderByRoundNumberAsc(jobIds).stream()
                .collect(Collectors.groupingBy(Round::getJobId));
    }

    private void requireJob(String jobId) {
        if (!placementRepository.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        }
    }
}
