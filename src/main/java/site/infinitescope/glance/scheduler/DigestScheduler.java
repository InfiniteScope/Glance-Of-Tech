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

    /**
     * 日报推送点：08:15 确保当日早报已生成（08:00 若失败则补跑）。
     * RSS 是动态渲染（/api/digest/feed.xml），日报 = 前日晚报 + 今日早报，
     * 此任务保证该组合此时已完整，读者轮询即可拿到。
     */
    @Scheduled(cron = "${glance.schedule.daily-report-cron:0 15 8 * * *}", zone = "${glance.timezone:Asia/Shanghai}")
    public void dailyReport() {
        LocalDate today = LocalDate.now(digestService.zoneId());
        try {
            if (!digestService.hasDigest(today, Period.MORNING)) {
                log.warn("morning digest missing at daily report time, generating now");
                digestService.generate(today, Period.MORNING, false);
            }
            log.info("daily report ready: {} (yesterday evening + today morning)", today);
        } catch (Exception e) {
            log.error("daily report task failed for {}", today, e);
            runStatus.recordFailure(e.getMessage());
        }
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
