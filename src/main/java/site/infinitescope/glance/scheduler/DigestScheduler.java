package site.infinitescope.glance.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.infinitescope.glance.model.Period;
import site.infinitescope.glance.service.DigestService;
import site.infinitescope.glance.service.RunStatus;

import java.time.LocalDate;

@Component
public class DigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

    private final DigestService digestService;
    private final RunStatus runStatus;

    public DigestScheduler(DigestService digestService, RunStatus runStatus) {
        this.digestService = digestService;
        this.runStatus = runStatus;
    }

    @Scheduled(cron = "${glance.schedule.morning-cron:0 0 8 * * *}", zone = "${glance.timezone:Asia/Shanghai}")
    public void morning() {
        run(Period.MORNING);
    }

    @Scheduled(cron = "${glance.schedule.evening-cron:0 0 20 * * *}", zone = "${glance.timezone:Asia/Shanghai}")
    public void evening() {
        run(Period.EVENING);
    }

    private void run(Period period) {
        LocalDate today = LocalDate.now(digestService.zoneId());
        try {
            digestService.generate(today, period, false);
        } catch (Exception e) {
            log.error("scheduled digest generation failed for {} {}", today, period.value(), e);
            runStatus.recordFailure(e.getMessage());
        }
    }
}
