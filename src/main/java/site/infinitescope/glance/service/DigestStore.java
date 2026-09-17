package site.infinitescope.glance.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.infinitescope.glance.model.Digest;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.repo.DigestRepository;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class DigestStore {

    private final DigestRepository digestRepository;

    public DigestStore(DigestRepository digestRepository) {
        this.digestRepository = digestRepository;
    }

    public boolean exists(LocalDate date, Period period) {
        return digestRepository.existsByDateAndPeriod(date, period);
    }

    @Transactional(readOnly = true)
    public Optional<Digest> find(LocalDate date, Period period) {
        return digestRepository.findByDateAndPeriod(date, period);
    }

    @Transactional(readOnly = true)
    public Optional<Digest> findLatest() {
        return digestRepository.findTop1ByOrderByGeneratedAtDesc()
                .flatMap(digest -> digestRepository.findWithItemsById(digest.getId()));
    }

    @Transactional
    public Digest saveReplacing(Digest digest, boolean replaceExisting) {
        if (replaceExisting) {
            digestRepository.findByDateAndPeriod(digest.getDate(), digest.getPeriod())
                    .ifPresent(digestRepository::delete);
            digestRepository.flush();
        }
        return digestRepository.save(digest);
    }
}
