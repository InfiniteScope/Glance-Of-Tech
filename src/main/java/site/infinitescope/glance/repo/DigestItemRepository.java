package site.infinitescope.glance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.infinitescope.glance.model.DigestItem;

import java.util.Collection;
import java.util.Set;

public interface DigestItemRepository extends JpaRepository<DigestItem, Long> {

    @Query("SELECT i.url FROM DigestItem i WHERE i.url IN :urls")
    Set<String> findExistingUrls(@Param("urls") Collection<String> urls);
}
