package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Participant;
import com.scoregrid.tournament.tournament.domain.port.in.GetParticipant;
import com.scoregrid.tournament.tournament.domain.port.out.ParticipantRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetParticipantUseCase implements GetParticipant {

    private final ParticipantRepository participantRepository;

    public GetParticipantUseCase(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public Participant execute(Long tournamentId, String userId) {
        return participantRepository.find(tournamentId, userId)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "User " + userId + " is not enrolled in tournament " + tournamentId));
    }
}
