package com.example.placement.service;

import com.example.placement.dto.ApplicationDetail;
import com.example.placement.dto.RoundDetail;
import com.example.placement.entity.Application;
import com.example.placement.entity.ApplicationRound;
import com.example.placement.entity.PlacementRound;
import com.example.placement.entity.Placement;
import com.example.placement.entity.RoundStatus;
import com.example.placement.entity.UsersEntries;
import com.example.placement.repository.ApplicationRepository;
import com.example.placement.repository.PlacementRepository;
import com.example.placement.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationServiceTest {

    private ApplicationRepository applicationRepository;
    private PlacementRepository placementRepository;
    private UsersRepository usersRepository;
    private MongoTemplate mongoTemplate;
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        placementRepository = mock(PlacementRepository.class);
        usersRepository = mock(UsersRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        applicationService = new ApplicationService(
                applicationRepository, placementRepository, usersRepository, mongoTemplate);
    }

    private void stubFindAllByIds(Integer... placementRounds) {
        Placement p = placement(placementRounds);
        when(usersRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.Set<String> ids = inv.getArgument(0);
            List<UsersEntries> result = new ArrayList<>();
            for (String id : ids) {
                if ("student1".equals(id)) result.add(user());
            }
            return result;
        });
        when(placementRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.Set<String> ids = inv.getArgument(0);
            List<Placement> result = new ArrayList<>();
            for (String id : ids) {
                if ("placement1".equals(id)) result.add(p);
            }
            return result;
        });
    }

    private Application application(Integer currentRound, String status, List<ApplicationRound> rounds) {
        Application application = new Application();
        application.setId("app1");
        application.setStudentId("student1");
        application.setPlacementId("placement1");
        application.setCurrentRound(currentRound);
        application.setStatus(status);
        application.setApplicationDate(LocalDateTime.now().minusDays(1));
        application.setLastUpdated(LocalDateTime.now());
        application.setRounds(rounds == null ? new ArrayList<>() : new ArrayList<>(rounds));
        return application;
    }

    private ApplicationRound round(int number, RoundStatus status) {
        ApplicationRound round = new ApplicationRound(number);
        round.setStatus(status);
        return round;
    }

    private ApplicationRound evaluated(int number, Double score, String remark) {
        ApplicationRound round = new ApplicationRound(number);
        round.setStatus(RoundStatus.QUALIFIED);
        round.setScore(score);
        round.setAdminRemark(remark);
        round.setEvaluatedAt(LocalDateTime.now());
        return round;
    }

    private Placement placement(Integer... numbers) {
        Placement placement = new Placement();
        placement.setId("placement1");
        placement.setCompany("ABC Technologies");
        List<PlacementRound> rounds = new ArrayList<>();
        String[] names = {"Online Assessment", "Technical Interview", "HR Interview", "Managerial Interview"};
        for (int i = 0; i < numbers.length; i++) {
            rounds.add(new PlacementRound(numbers[i], names[Math.min(i, names.length - 1)]));
        }
        placement.setRounds(rounds);
        return placement;
    }

    private UsersEntries user() {
        UsersEntries user = new UsersEntries();
        user.setId("student1");
        user.setName("Rahul Sharma");
        user.setEnrollmentno("ENR001");
        user.setEmail("rahul@example.com");
        return user;
    }

    private void stubFindAndModifyResult(Application updated) {
        when(mongoTemplate.findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Application.class))).thenReturn(updated);
    }

    @Test
    void applyInitializesApplicationOnRoundOnePending() {
        when(applicationRepository.findByStudentIdAndPlacementId("student1", "placement1"))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        applicationService.apply("student1", "placement1", "https://example.com/resume.pdf", "Data Science");
        verify(applicationRepository).save(captor.capture());

        Application saved = captor.getValue();
        assertEquals("https://example.com/resume.pdf", saved.getResumeUrl());
        assertEquals("Data Science", saved.getSelectedCourse());
        assertEquals("IN_PROGRESS", saved.getStatus());
        assertEquals(1, saved.getCurrentRound());
        assertEquals(1, saved.getRounds().size());
        assertEquals(1, saved.getRounds().get(0).getRound());
        assertEquals(RoundStatus.PENDING, saved.getRounds().get(0).getStatus());
        assertNotNull(saved.getApplicationDate());
        assertNotNull(saved.getLastUpdated());
    }

    @Test
    void applicationDetailsKeepSelectedCourseStudentBatchAndExactJobSalary() {
        Application application = application(1, "IN_PROGRESS", List.of());
        application.setSelectedCourse("Data Science");
        UsersEntries student = user();
        student.setBatch("2022-2026");
        student.setCourse("Computer Science");
        student.setBranch("CSE");
        Placement job = placement();
        job.setBatch("2021-2027");
        job.setBranch("All Engineering");
        job.setSalaryLpa("4.1 - 7.8");

        ApplicationDetail detail = ApplicationService.mapDetail(application, student, job, List.of());

        assertEquals("Data Science", detail.getSelectedCourse());
        assertEquals("Computer Science", detail.getStudentCourse());
        assertEquals("2022-2026", detail.getBatch());
        assertEquals("2022-2026", detail.getStudentBatch());
        assertEquals("2021-2027", detail.getEligibleBatch());
        assertEquals("4.1 - 7.8", detail.getSalary());
        assertEquals("CSE", detail.getBranch());
        assertEquals("All Engineering", detail.getEligibleBranch());
    }

    @Test
    void qualifyRoundOneMarksQualifiedCreatesRoundTwoPending() {
        Application current = application(1, "IN_PROGRESS",
                List.of(round(1, RoundStatus.PENDING)));
        Application updated = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 85.0, "Good assessment"),
                round(2, RoundStatus.PENDING)));

        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2, 3)));
        stubFindAllByIds(1, 2, 3);
        stubFindAndModifyResult(updated);

        ApplicationDetail detail = applicationService.qualify("app1", 1, 85.0, "Good assessment");

        assertEquals(2, detail.getCurrentRound());
        assertEquals("IN_PROGRESS", detail.getStatus());
        assertEquals(3, detail.getTotalRounds());
        assertEquals(2, detail.getRounds().size());
        RoundDetail first = detail.getRounds().get(0);
        assertEquals("QUALIFIED", first.getStatus());
        assertEquals(85.0, first.getScore());
        assertEquals("Good assessment", first.getAdminRemark());
        assertNotNull(first.getEvaluatedAt());
        assertEquals("Online Assessment", first.getName());
        RoundDetail second = detail.getRounds().get(1);
        assertEquals("PENDING", second.getStatus());
        assertNull(second.getScore());
        assertEquals("Technical Interview", second.getName());
    }

    @Test
    void evaluationUpdateTargetsPendingPositionalEntry() {
        Application current = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));
        stubFindAndModifyResult(application(2, "IN_PROGRESS", List.of(
                evaluated(1, 70.0, null), round(2, RoundStatus.PENDING))));

        applicationService.qualify("app1", 1, 70.0, null);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<org.springframework.data.mongodb.core.query.UpdateDefinition> updateCaptor = ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(Application.class));

        org.bson.Document queryObject = queryCaptor.getValue().getQueryObject();
        StringBuilder flattened = new StringBuilder();
        flatten(queryObject, flattened);
        assertTrue(flattened.toString().contains("app1"));
        assertTrue(flattened.toString().contains("currentRound"));
        assertTrue(flattened.toString().contains("PENDING"));

        org.bson.Document updateObject = updateCaptor.getValue().getUpdateObject();
        StringBuilder updateRendered = new StringBuilder();
        flatten(updateObject, updateRendered);
        String updateText = updateRendered.toString();
        assertTrue(updateText.contains("QUALIFIED"));
        assertTrue(updateText.contains("IN_PROGRESS"));
        assertTrue(updateText.contains("evaluatedAt"));
    }

    private static void flatten(Object node, StringBuilder out) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                out.append(key).append(' ');
                flatten(value, out);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(item -> flatten(item, out));
        } else if (node != null) {
            out.append(node).append(' ');
        }
    }

    @Test
    void qualifyingFinalRoundMarksSelectedWithoutExtraRound() {
        Application current = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 80.0, null), round(2, RoundStatus.PENDING)));
        Application updated = application(2, "SELECTED", List.of(
                evaluated(1, 80.0, null),
                evaluated(2, 90.0, "Excellent")));

        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));
        stubFindAllByIds(1, 2);
        stubFindAndModifyResult(updated);

        ApplicationDetail detail = applicationService.qualify("app1", 2, 90.0, "Excellent");

        assertEquals("SELECTED", detail.getStatus());
        assertEquals(2, detail.getCurrentRound());
        assertEquals(2, detail.getRounds().size());
        assertEquals("QUALIFIED", detail.getRounds().get(1).getStatus());
    }

    @Test
    void rejectingCurrentRoundMarksRejectedWithoutNewRound() {
        Application current = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        Application updated = application(1, "REJECTED", List.of(round(1, RoundStatus.REJECTED)));

        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2, 3)));
        stubFindAllByIds(1, 2, 3);
        stubFindAndModifyResult(updated);

        ApplicationDetail detail = applicationService.reject("app1", 1, 40.0, "Did not meet criteria");

        assertEquals("REJECTED", detail.getStatus());
        assertEquals(1, detail.getCurrentRound());
        assertEquals("REJECTED", detail.getRounds().get(0).getStatus());
    }

    @Test
    void evaluatingWrongRoundIsRejected() {
        Application current = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.qualify("app1", 2, null, null));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("currently on Round 1"));
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Application.class));
    }

    @Test
    void evaluatingUnconfiguredRoundIsBadRequest() {

        Application current = application(3, "IN_PROGRESS", List.of(
                evaluated(1, 80.0, null), evaluated(2, 75.0, null), round(3, RoundStatus.PENDING)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.qualify("app1", 3, null, null));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("not configured"));
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Application.class));
    }

    @Test
    void alreadyEvaluatedRoundCannotBeEvaluatedAgain() {

        Application current = application(1, "IN_PROGRESS", List.of(evaluated(1, 85.0, null)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.qualify("app1", 1, null, null));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("already been evaluated"));
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Application.class));
    }

    @Test
    void terminalApplicationsCannotBeEvaluated() {
        Application selected = application(2, "SELECTED",
                List.of(evaluated(1, 85.0, null), evaluated(2, 90.0, null)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(selected));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.reject("app1", 2, null, null));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("already selected"));
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Application.class));
    }

    @Test
    void negativeScoreIsRejected() {
        Application current = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.qualify("app1", 1, -5.0, null));

        assertEquals(400, exception.getStatusCode().value());
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Application.class));
    }

    @Test
    void legacyDocumentWithoutRoundFieldsStartsAtRoundOne() {

        Application legacy = application(null, "Applied", new ArrayList<>());
        Application updated = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 75.0, null), round(2, RoundStatus.PENDING)));

        when(applicationRepository.findById("app1")).thenReturn(Optional.of(legacy));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));
        stubFindAllByIds(1, 2);
        stubFindAndModifyResult(updated);

        ApplicationDetail detail = applicationService.qualify("app1", null, 75.0, null);

        assertEquals(2, detail.getCurrentRound());

        InOrder order = inOrder(mongoTemplate);
        order.verify(mongoTemplate).updateFirst(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class), eq(Application.class));
        order.verify(mongoTemplate).findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Application.class));
    }

    @Test
    void duplicateQualifyLosingRaceReturnsPreciseConflict() {
        Application current = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        Application advancedByOtherRequest = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 85.0, null), round(2, RoundStatus.PENDING)));

        when(applicationRepository.findById("app1"))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(advancedByOtherRequest));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));
        when(mongoTemplate.findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Application.class))).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.qualify("app1", 1, null, null));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("already been evaluated")
                || exception.getReason().contains("concurrently"));
    }

    @Test
    void listingEnrichesLegacyDocumentsWithDefaults() {
        Application legacy = application(null, "Selected", new ArrayList<>());
        when(applicationRepository.findAll()).thenReturn(List.of(legacy));
        stubFindAllByIds(1, 2);

        List<ApplicationDetail> details = applicationService.getAllApplications();

        assertEquals(1, details.size());
        ApplicationDetail detail = details.get(0);
        assertEquals("student1", detail.getStudentId());
        assertEquals("placement1", detail.getPlacementId());
        assertEquals(1, detail.getCurrentRound());
        assertEquals(2, detail.getTotalRounds());
        assertTrue(detail.getRounds().isEmpty());
        assertEquals("Rahul Sharma", detail.getStudentName());
        assertEquals("ABC Technologies", detail.getCompany());
    }

    @Test
    void listingResolvesStudentByEnrollmentWhenIdDiffers() {
        Application legacy = application(null, "Applied", new ArrayList<>());
        legacy.setStudentId("2021CS001234");
        when(applicationRepository.findAll()).thenReturn(List.of(legacy));

        UsersEntries record = new UsersEntries();
        record.setId("a-different-object-id");
        record.setName("Rahul Sharma");
        record.setEnrollmentno("2021CS001234");
        record.setEmail("rahul@example.com");
        record.setPhoneno("9999999999");
        when(usersRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.Set<String> ids = inv.getArgument(0);
            return ids.contains("2021CS001234") ? List.of(record) : new ArrayList<>();
        });
        when(placementRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.Set<String> ids = inv.getArgument(0);
            return ids.contains("placement1") ? List.of(placement(1, 2)) : new ArrayList<>();
        });

        List<ApplicationDetail> details = applicationService.getAllApplications();

        assertEquals(1, details.size());
        ApplicationDetail detail = details.get(0);
        assertEquals("Rahul Sharma", detail.getStudentName());
        assertEquals("2021CS001234", detail.getEnrollmentno());
        assertEquals("rahul@example.com", detail.getStudentEmail());
        assertEquals("9999999999", detail.getPhoneNo());
    }

    @Test
    void advanceResolvesCurrentRoundAndCreatesNextPendingRound() {
        Application current = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 70.0, "OK"), round(2, RoundStatus.PENDING)));
        Application updated = application(3, "IN_PROGRESS", List.of(
                evaluated(1, 70.0, "OK"),
                evaluated(2, null, "Cleared"),
                round(3, RoundStatus.PENDING)));

        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2, 3)));
        stubFindAllByIds(1, 2, 3);
        stubFindAndModifyResult(updated);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<org.springframework.data.mongodb.core.query.UpdateDefinition> updateCaptor = ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.UpdateDefinition.class);
        doReturn(updated).when(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(Application.class));

        ApplicationDetail detail = applicationService.advance("app1", null, "Cleared");

        assertEquals("IN_PROGRESS", detail.getStatus());
        assertEquals(3, detail.getCurrentRound());
        assertEquals(3, detail.getRounds().size());
        assertEquals("PENDING", detail.getRounds().get(2).getStatus());

        org.bson.Document queryObject = queryCaptor.getValue().getQueryObject();
        StringBuilder flattened = new StringBuilder();
        flatten(queryObject, flattened);
        assertTrue(flattened.toString().contains("currentRound 2"));

        org.bson.Document updateObject = updateCaptor.getValue().getUpdateObject();
        StringBuilder updateRendered = new StringBuilder();
        flatten(updateObject, updateRendered);
        String updateText = updateRendered.toString();
        assertTrue(updateText.contains("QUALIFIED"));
        assertTrue(updateText.contains("PENDING"));
        assertTrue(updateText.contains("currentRound 3"));
    }

    @Test
    void advanceOnFinalConfiguredRoundMarksSelected() {
        Application current = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 70.0, null), round(2, RoundStatus.PENDING)));
        Application updated = application(2, "SELECTED", List.of(
                evaluated(1, 70.0, null), evaluated(2, 88.0, null)));

        when(applicationRepository.findById("app1")).thenReturn(Optional.of(current));

        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));
        stubFindAllByIds(1, 2);
        stubFindAndModifyResult(updated);

        ApplicationDetail detail = applicationService.advance("app1", 88.0, null);

        assertEquals("SELECTED", detail.getStatus());
        assertEquals(2, detail.getCurrentRound());
        assertEquals(2, detail.getTotalRounds());
    }

    @Test
    void advanceTerminalApplicationIsConflict() {
        Application rejectedApp = application(2, "REJECTED", List.of(
                evaluated(1, 40.0, "Weak fundamentals"), round(2, RoundStatus.REJECTED)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(rejectedApp));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.advance("app1", null, null));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("terminal")
                || exception.getReason().contains("rejected"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void bulkAdvanceMovesEligibleCandidatesAndReportsFailuresIndependently() {
        Application eligible = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        eligible.setId("appA");
        Application stale = application(1, "IN_PROGRESS", List.of(evaluated(1, 85.0, null)));
        stale.setId("appB");
        Application movedOn = application(2, "IN_PROGRESS", List.of(
                evaluated(1, null, "Cleared"), round(2, RoundStatus.PENDING)));
        movedOn.setId("appA");

        when(applicationRepository.findById("appA"))
                .thenReturn(Optional.of(eligible))
                .thenReturn(Optional.of(eligible));
        when(applicationRepository.findById("appB"))
                .thenReturn(Optional.of(stale))
                .thenReturn(Optional.of(stale));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));
        stubFindAllByIds(1, 2);
        stubFindAndModifyResult(movedOn);

        Map<String, Object> summary = applicationService.bulkAdvance(List.of("appA", "appB"), "Cleared");

        assertEquals(1, summary.get("moved"));
        assertEquals(1, summary.get("failed"));
        List<Map<String, Object>> results = (List<Map<String, Object>>) summary.get("results");
        assertEquals("MOVED", results.get(0).get("status"));
        assertEquals(1, results.get(0).get("fromRound"));
        assertEquals(2, results.get(0).get("toRound"));
        assertNotNull(results.get(0).get("application"));
        assertEquals("FAILED", results.get(1).get("status"));
        assertTrue(((String) results.get(1).get("message")).contains("already been evaluated"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void bulkRejectHandlesMissingApplicationWithoutBlockingOthers() {
        Application eligible = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        eligible.setId("appA");
        Application rejected = application(1, "REJECTED", List.of(round(1, RoundStatus.REJECTED)));
        rejected.setId("appA");

        when(applicationRepository.findById("appA"))
                .thenReturn(Optional.of(eligible))
                .thenReturn(Optional.of(eligible));
        when(applicationRepository.findById("ghost"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Application not found"));
        when(placementRepository.findById("placement1")).thenReturn(Optional.of(placement(1, 2)));
        stubFindAllByIds(1, 2);
        stubFindAndModifyResult(rejected);

        Map<String, Object> summary = applicationService.bulkReject(
                java.util.Arrays.asList("appA", "ghost"), "Did not meet requirements");

        assertEquals(1, summary.get("moved"));
        assertEquals(1, summary.get("failed"));
        List<Map<String, Object>> results = (List<Map<String, Object>>) summary.get("results");
        assertEquals("REJECTED", results.get(0).get("status"));
        assertEquals(1, results.get(0).get("fromRound"));
        assertEquals(1, results.get(0).get("toRound"));
        assertEquals("FAILED", results.get(1).get("status"));
        assertTrue(((String) results.get(1).get("message")).contains("not found"));
    }

    @Test
    void setRoundResetsEvaluatedEntryToPendingAndMovesCurrent() {
        Application current = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 85.0, null), round(2, RoundStatus.PENDING)));
        Application updated = application(1, "IN_PROGRESS", List.of(
                round(1, RoundStatus.PENDING), evaluated(2, 70.0, null)));

        when(applicationRepository.findById("app1"))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(current));
        stubFindAllByIds(1, 2);

        ArgumentCaptor<org.springframework.data.mongodb.core.query.UpdateDefinition> updateCaptor =
                ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.UpdateDefinition.class);
        doReturn(updated).when(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(Application.class));

        ApplicationDetail detail = applicationService.setCurrentRound("app1", 1, "Re-evaluation");

        assertEquals(1, detail.getCurrentRound());
        assertEquals("PENDING", detail.getRounds().get(0).getStatus());

        StringBuilder rendered = new StringBuilder();
        flatten(updateCaptor.getValue().getUpdateObject(), rendered);
        String text = rendered.toString();
        assertTrue(text.contains("currentRound 1"));
        assertTrue(text.contains("IN_PROGRESS"));
    }

    @Test
    void setRoundAppendsMissingEntryForTargetRound() {
        Application current = application(1, "IN_PROGRESS", List.of(evaluated(1, 60.0, null)));
        Application updated = application(2, "IN_PROGRESS", List.of(
                evaluated(1, 60.0, null), round(2, RoundStatus.PENDING)));

        when(applicationRepository.findById("app1"))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(current));
        stubFindAllByIds(1, 2);
        stubFindAndModifyResult(updated);

        ApplicationDetail detail = applicationService.setCurrentRound("app1", 2, null);

        assertEquals(2, detail.getCurrentRound());
        assertEquals(2, detail.getRounds().size());
        assertEquals("PENDING", detail.getRounds().get(1).getStatus());
    }

    @Test
    void setRoundTerminalApplicationIsConflict() {
        Application selected = application(2, "SELECTED",
                List.of(evaluated(1, 85.0, null), evaluated(2, 90.0, null)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(selected));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.setCurrentRound("app1", 1, null));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("already selected"));
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Application.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void bulkSetRoundReportsFailuresIndependently() {
        Application eligible = application(3, "IN_PROGRESS", List.of(
                evaluated(1, 80.0, null), evaluated(2, 75.0, null), round(3, RoundStatus.PENDING)));
        eligible.setId("appA");
        Application updated = application(1, "IN_PROGRESS", List.of(round(1, RoundStatus.PENDING)));
        updated.setId("appA");

        when(applicationRepository.findById("appA"))
                .thenReturn(Optional.of(eligible))
                .thenReturn(Optional.of(eligible));
        when(applicationRepository.findById("ghost"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Application not found"));
        stubFindAllByIds(1, 2, 3);
        stubFindAndModifyResult(updated);

        Map<String, Object> summary = applicationService.bulkSetRound(
                java.util.Arrays.asList("appA", "ghost"), 1, "Reset");

        assertEquals(1, summary.get("moved"));
        assertEquals(1, summary.get("failed"));
        List<Map<String, Object>> results = (List<Map<String, Object>>) summary.get("results");
        assertEquals("SET", results.get(0).get("status"));
        assertEquals(3, results.get(0).get("fromRound"));
        assertEquals(1, results.get(0).get("toRound"));
        assertNotNull(results.get(0).get("application"));
        assertEquals("FAILED", results.get(1).get("status"));
    }

    @Test
    void updateStatusRejectsUnknownStatus() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> applicationService.updateStatus("app1", "Made-Up Status", null));

        assertEquals(400, exception.getStatusCode().value());
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void updateStatusAcceptsLegacyStatusAndStampsNow() {
        Application existing = application(2, "IN_PROGRESS", List.of(round(2, RoundStatus.PENDING)));
        when(applicationRepository.findById("app1")).thenReturn(Optional.of(existing));
        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);

        boolean updated = applicationService.updateStatus("app1", "Under Review", "Reviewed");

        assertTrue(updated);
        verify(applicationRepository).save(captor.capture());
        assertEquals("Under Review", captor.getValue().getStatus());
        assertEquals("Reviewed", captor.getValue().getAdminRemark());
        assertTrue(captor.getValue().getLastUpdated().isAfter(java.time.LocalDateTime.now().minusMinutes(1)));
    }

    private Placement job(String batch, Double minTenth, Double minTwelfth, Double minGrad, Double minCgpa) {
        Placement job = new Placement();
        job.setId("placement1");
        job.setBatch(batch);
        job.setMinTenth(minTenth);
        job.setMinTwelfth(minTwelfth);
        job.setMinGraduation(minGrad);
        job.setMinCgpa(minCgpa);
        return job;
    }

    private UsersEntries student(String batch, Double tenth, Double twelfth, Double grad, Double cgpa) {
        UsersEntries student = new UsersEntries();
        student.setId("student1");
        student.setName("Rahul Sharma");
        student.setBatch(batch);
        student.setTenthPercentage(tenth);
        student.setTwelfthPercentage(twelfth);
        student.setGraduationPercentage(grad);
        student.setCgpa(cgpa);
        return student;
    }

    @Test
    void eligibleWhenBatchOverlapsAndAllCutoffsMet() {
        Placement job = job("2021-2025", 60.0, 65.0, 70.0, 7.5);
        UsersEntries student = student("2021-2025", 82.0, 78.0, 74.0, 8.1);

        assertTrue(applicationService.isEligible(job, student));
    }

    @Test
    void ineligibleWhenBatchHasNoOverlap() {
        Placement job = job("2027-2028", null, null, null, null);
        UsersEntries student = student("2021-2025", null, null, null, null);

        assertEquals(false, applicationService.isEligible(job, student));
    }

    @Test
    void ineligibleWhenSingleFieldBelowCutoff() {
        Placement job = job("2021-2025", 60.0, 65.0, 70.0, 7.5);
        UsersEntries student = student("2023-2027", 82.0, 78.0, 55.0, 8.1);

        assertEquals(false, applicationService.isEligible(job, student));
    }

    @Test
    void ineligibleWhenStudentValueMissingButRequirementSet() {
        Placement job = job("2021-2025", 60.0, null, null, null);
        UsersEntries student = student("2021-2025", null, null, null, null);

        assertEquals(false, applicationService.isEligible(job, student));
    }

    @Test
    void eligibleWhenJobHasNoRequirements() {
        Placement job = job(null, null, null, null, null);
        UsersEntries student = student("2021-2025", 82.0, 78.0, 74.0, 8.1);

        assertTrue(applicationService.isEligible(job, student));
    }

    @Test
    void ineligibleWhenNullJobOrNullStudent() {
        assertTrue(applicationService.isEligible(null, student("2021-2025", null, null, null, null)) == false);
        assertTrue(applicationService.isEligible(job(null, null, null, null, null), null) == false);
    }
}
