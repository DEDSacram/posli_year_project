package cz.bazar.scheduler;

import cz.bazar.repository.ReportRepository;
import cz.bazar.service.AuditService;
import cz.bazar.service.Dac7Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class Dac7Scheduler {
  private final Dac7Service dac7;
  private final AuditService audit;
  private final ReportRepository reports;

  /** Cron 1x rocne: DAC7 export + souhrn moderace (leden). */
  @Scheduled(cron = "0 0 3 5 1 *")
  public void yearly() {
    int year = LocalDate.now().getYear() - 1;
    var rows = dac7.collect(year);
    log.info("DAC7 rocni export year={} reportable={}", year, rows.size());
    audit.log(null, "DAC7_YEARLY_EXPORT", "dac7", (long) year);
    long open = reports.countByStatus("open");
    long approved = reports.countByStatus("approved");
    long removed = reports.countByStatus("removed");
    log.info("Moderace rocni souhrn open={} approved={} removed={}", open, approved, removed);
    audit.log(null, "MODERATION_YEARLY_SUMMARY", "moderation", null);
  }
}
