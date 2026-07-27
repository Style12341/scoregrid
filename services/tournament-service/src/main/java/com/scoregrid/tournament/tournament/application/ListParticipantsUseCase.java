package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Participant;
import com.scoregrid.tournament.tournament.domain.port.in.ListParticipants;
import com.scoregrid.tournament.tournament.domain.port.out.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListParticipantsUseCase implements ListParticipants {

    private final ParticipantRepository participantRepository;

    public ListParticipantsUseCase(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public List<Participant> execute(Long tournamentId) {
        return participantRepository.findByTournamentId(tournamentId);
    }
}
