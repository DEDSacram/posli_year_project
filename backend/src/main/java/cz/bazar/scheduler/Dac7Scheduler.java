package cz.bazar.scheduler;

import cz.bazar.repository.ReportRepository;
import cz.bazar.service.AuditService;
import cz.bazar.service.Dac7Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class Dac7Scheduler {
  private static final Logger log = LoggerFactory.getLogger(Dac7Scheduler.class);
  private final Dac7Service dac7;
  private final AuditService audit;
  private final ReportRepository reports;

  public Dac7Scheduler(Dac7Service dac7, AuditService audit, ReportRepository reports) {
    this.dac7 = dac7; this.audit = audit; this.reports = reports;
  }

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
