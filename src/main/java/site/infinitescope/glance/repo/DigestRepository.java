package site.infinitescope.glance.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import site.infinitescope.glance.model.Digest;
import site.infinitescope.glance.model.Period;

import java.time.LocalDate;
import java.util.Optional;

public interface DigestRepository extends JpaRepository<Digest, Long> {

    boolean existsByDateAndPeriod(LocalDate date, Period period);

    @EntityGraph(attributePaths = "items")
    Optional<Digest> findByDateAndPeriod(LocalDate date, Period period);

    Optional<Digest> findTop1ByOrderByGeneratedAtDesc();

    @EntityGraph(attributePaths = "items")
    Optional<Digest> findWithItemsById(Long id);

    Page<Digest> findAllByOrderByGeneratedAtDesc(Pageable pageable);
}
