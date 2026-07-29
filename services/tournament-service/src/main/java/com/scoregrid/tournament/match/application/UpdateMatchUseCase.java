package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.model.TeamRef;
import com.scoregrid.tournament.match.domain.port.in.UpdateMatch;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.phase.domain.port.out.PhaseRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class UpdateMatchUseCase implements UpdateMatch {

    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TeamRepository teamRepository;
    private final GroupRepository groupRepository;
    private final PhaseRepository phaseRepository;
    private final GroupTeamRepository groupTeamRepository;
    private final MatchEventPublisher eventPublisher;

    public UpdateMatchUseCase(MatchRepository matchRepository,
                               TournamentRepository tournamentRepository,
                               TournamentTeamRepository tournamentTeamRepository,
                               TeamRepository teamRepository,
                               GroupRepository groupRepository,
                               PhaseRepository phaseRepository,
                               GroupTeamRepository groupTeamRepository,
                               MatchEventPublisher eventPublisher) {
        this.matchRepository = matchRepository;
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.teamRepository = teamRepository;
        this.groupRepository = groupRepository;
        this.phaseRepository = phaseRepository;
        this.groupTeamRepository = groupTeamRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Match execute(Command command) {
        var match = matchRepository.findById(command.id())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Match not found: " + command.id()));

        var tournament = tournamentRepository.findById(match.getTournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + match.getTournamentId()));

        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Tournament is not ACTIVE");
        }

        MatchStatus previousStatus = match.getStatus();
        Instant previousStartTime = match.getStartTime();

        // Validate group/phase constraints if they changed
        if (command.groupId() != null) {
            validateGroupExists(command.groupId(), tournament.getId());
        }
        if (command.phaseId() != null) {
            validatePhaseExists(command.phaseId(), tournament.getId());
        }

        // Validate team registrations for both teams
        validateTeamRegistered(tournament.getId(), command.homeTeamId());
        validateTeamRegistered(tournament.getId(), command.awayTeamId());

        // If group match, validate both teams are in the group
        if (command.groupId() != null) {
            validateTeamInGroup(command.groupId(), command.homeTeamId());
            validateTeamInGroup(command.groupId(), command.awayTeamId());
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

        // Apply group/phase/team field changes
        match.setGroupId(command.groupId());
        match.setPhaseId(command.phaseId());
        match.setHomeTeam(homeRef);
        match.setAwayTeam(awayRef);

        // Apply status transition if requested
        try {
            applyStatusTransition(match, command.status(), command.startTime());
        } catch (IllegalStateException e) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "INVALID_MATCH_STATE", e.getMessage());
        }

        // Apply startTime change if status didn't already handle it
        if (!command.startTime().equals(match.getStartTime())
                && match.getStatus() != MatchStatus.POSTPONED) {
            match.setStartTime(command.startTime());
        }

        var saved = matchRepository.save(match);

        // Publish event only if startTime or status changed
        boolean changed = !saved.getStatus().equals(previousStatus)
                || !saved.getStartTime().equals(previousStartTime);
        if (changed) {
            eventPublisher.updated(saved, tournament.getStatus());
        }

        return saved;
    }

    private void applyStatusTransition(Match match, MatchStatus targetStatus, Instant newStartTime) {
        if (targetStatus == match.getStatus()) {
            return; // no-op
        }
        switch (targetStatus) {
            case IN_PROGRESS -> match.start();
            case FINISHED -> match.finish();
            case POSTPONED -> match.postpone();
            case CANCELLED -> match.cancel();
            case SCHEDULED -> match.reschedule(newStartTime);
        }
    }

    private void validateTeamRegistered(Long tournamentId, Long teamId) {
        if (!tournamentTeamRepository.existsByTournamentIdAndTeamId(tournamentId, teamId)) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "NOT_REGISTERED",
                    "Team " + teamId + " not registered in this tournament");
        }
    }

    private void validateTeamInGroup(Long groupId, Long teamId) {
        if (!groupTeamRepository.existsByGroupIdAndTeamId(groupId, teamId)) {
            throw new DomainException(ErrorKind.UNPROCESSABLE, "NOT_IN_GROUP",
                    "Team " + teamId + " is not in group " + groupId);
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
}
