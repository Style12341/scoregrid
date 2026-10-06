package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Participant;
import com.scoregrid.tournament.tournament.domain.port.in.ListParticipantsUseCase;
import com.scoregrid.tournament.tournament.domain.port.out.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListParticipantsService implements ListParticipantsUseCase {

    private final ParticipantRepository participantRepository;

    public ListParticipantsService(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public List<Participant> execute(Long tournamentId) {
        return participantRepository.findByTournamentId(tournamentId);
    }
}
