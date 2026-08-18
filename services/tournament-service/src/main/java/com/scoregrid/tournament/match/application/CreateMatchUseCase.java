package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.TeamRef;
import com.scoregrid.tournament.match.domain.port.in.CreateMatch;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.phase.domain.port.out.PhaseRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class CreateMatchUseCase implements CreateMatch {

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TeamRepository teamRepository;
    private final GroupRepository groupRepository;
    private final PhaseRepository phaseRepository;
    private final GroupTeamRepository groupTeamRepository;
    private final MatchRepository matchRepository;
    private final MatchEventPublisher eventPublisher;

    public CreateMatchUseCase(TournamentRepository tournamentRepository,
                               TournamentTeamRepository tournamentTeamRepository,
                               TeamRepository teamRepository,
                               GroupRepository groupRepository,
                               PhaseRepository phaseRepository,
                               GroupTeamRepository groupTeamRepository,
                               MatchRepository matchRepository,
                               MatchEventPublisher eventPublisher) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.teamRepository = teamRepository;
        this.groupRepository = groupRepository;
        this.phaseRepository = phaseRepository;
        this.groupTeamRepository = groupTeamRepository;
        this.matchRepository = matchRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Match execute(Command command) {
        var tournament = tournamentRepository.findById(command.tournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + command.tournamentId()));

        if (tournament.getStatus() != TournamentStatus.DRAFT
                && tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Tournament is not configurable in state " + tournament.getStatus());
        }

        validateCommand(command);

        // Validate teams are registered in the tournament
        validateTeamRegistered(command.tournamentId(), command.homeTeamId());
        validateTeamRegistered(command.tournamentId(), command.awayTeamId());

        // Validate group/phase constraints
        if (command.groupId() != null) {
            validateGroupExists(command.groupId(), command.tournamentId());
            validateTeamInGroup(command.groupId(), command.homeTeamId());
            validateTeamInGroup(command.groupId(), command.awayTeamId());
        } else {
            validatePhaseExists(command.phaseId(), command.tournamentId());
        }

        // Resolve team references
        var homeTeam = teamRepository.findById(command.homeTeamId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Team not found: " + command.homeTeamId()));
        var awayTeam = teamRepository.findById(command.awayTeamId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Team not found: " + command.awayTeamId()));

        TeamRef homeRef = TeamRef.of(homeTeam.getId(), homeTeam.getName(), homeTeam.getShortName());
        TeamRef awayRef = TeamRef.of(awayTeam.getId(), awayTeam.getName(), awayTeam.getShortName());

        Instant now = Instant.now();
        Match match;
        try {
            match = Match.create(command.tournamentId(), command.groupId(), command.phaseId(),
                    homeRef, awayRef, command.startTime(), now);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "VALIDATION_FAILED",
                    e.getMessage());
        }

        var saved = matchRepository.save(match);
        eventPublisher.scheduled(saved, tournament.getStatus());
        return saved;
    }

    private void validateCommand(Command command) {
        if (command.homeTeamId() == null || command.awayTeamId() == null) {
            throw validation("Both homeTeamId and awayTeamId are required");
        }
        if (command.homeTeamId().equals(command.awayTeamId())) {
            throw validation("Home team and away team must differ");
        }
        if ((command.groupId() == null) == (command.phaseId() == null)) {
            throw validation("Exactly one of groupId or phaseId is required");
        }
        if (command.startTime() == null) {
            throw validation("startTime is required");
        }
    }

    private DomainException validation(String message) {
        return new DomainException(ErrorKind.UNPROCESSABLE, "VALIDATION_FAILED", message);
    }

    private void validateTeamRegistered(Long tournamentId, Long teamId) {
        if (!tournamentTeamRepository.existsByTournamentIdAndTeamId(tournamentId, teamId)) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "VALIDATION_FAILED",
                    "Team " + teamId + " not registered in this tournament");
        }
    }

    private void validateGroupExists(Long groupId, Long tournamentId) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Group not found: " + groupId));
        if (!group.getTournamentId().equals(tournamentId)) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "VALIDATION_FAILED",
                    "Group " + groupId + " does not belong to tournament " + tournamentId);
        }
    }

    private void validatePhaseExists(Long phaseId, Long tournamentId) {
        var phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Phase not found: " + phaseId));
        if (!phase.getTournamentId().equals(tournamentId)) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "VALIDATION_FAILED",
                    "Phase " + phaseId + " does not belong to tournament " + tournamentId);
        }
    }

    private void validateTeamInGroup(Long groupId, Long teamId) {
        if (!groupTeamRepository.existsByGroupIdAndTeamId(groupId, teamId)) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "VALIDATION_FAILED",
                    "Team " + teamId + " is not in group " + groupId);
        }
    }
}
