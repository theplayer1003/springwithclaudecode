package com.study.board.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    @Bean
    public Tasklet helloTasklet() {
        return (contribution, chunkContext) -> {
            System.out.println("Hello, Spring Batch");
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step helloStep(JobRepository jobRepository, Tasklet tasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("stepOne", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Job helloJob(JobRepository jobRepository, Step step) {
        return new JobBuilder("jobOne", jobRepository)
                .start(step)
                .build();
    }
}
