package com.study.board.batch;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchScheduler {

    private JobLauncher jobLauncher;

    private Job printTitleToUppercaseJob;

    private Job archivePostJob;

    public BatchScheduler(JobLauncher jobLauncher, Job printTitleToUppercaseJob, Job archivePostJob) {
        this.jobLauncher = jobLauncher;
        this.printTitleToUppercaseJob = printTitleToUppercaseJob;
        this.archivePostJob = archivePostJob;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void runBatchJob() {
        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("requestDate", now)
                    .toJobParameters();

            jobLauncher.run(printTitleToUppercaseJob, jobParameters);

        } catch (Exception e) {

        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void runArchiveBatchJob() {
        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("requestDate", now)
                    .toJobParameters();

            jobLauncher.run(archivePostJob, jobParameters);
        } catch (Exception e) {

        }
    }
}
