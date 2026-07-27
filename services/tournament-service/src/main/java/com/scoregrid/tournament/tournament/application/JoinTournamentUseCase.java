package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Participant;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.JoinTournament;
import com.scoregrid.tournament.tournament.domain.port.out.ParticipantRepository;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JoinTournamentUseCase implements JoinTournament {

    private final TournamentRepository tournamentRepository;
    private final ParticipantRepository participantRepository;

    public JoinTournamentUseCase(TournamentRepository tournamentRepository,
                                  ParticipantRepository participantRepository) {
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    public Participant execute(Command command) {
        var tournament = tournamentRepository.findById(command.tournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + command.tournamentId()));

        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Tournament is not ACTIVE. Current status: " + tournament.getStatus());
        }

        if (participantRepository.exists(command.tournamentId(), command.userId())) {
            // Error code CONFLICT is tournament-service internal (not in cross-service
            // error list at docs/contracts.md). The frontend branches on HTTP 409, not
            // the error string, so internal codes are fine as long as the status is correct.
            throw new DomainException(ErrorKind.CONFLICT, "CONFLICT",
                    "User " + command.userId() + " is already enrolled in tournament " + command.tournamentId());
        }

        var participant = Participant.join(command.tournamentId(), command.userId());
        return participantRepository.save(participant);
    }
}
