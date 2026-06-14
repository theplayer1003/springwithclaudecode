package com.study.board.batch;

import com.study.board.entity.ArchivedPost;
import com.study.board.entity.Post;
import com.study.board.repository.ArchivedPostRepository;
import com.study.board.repository.PostRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableScheduling
public class BatchConfig {

    private final PostRepository postRepository;

    private final ArchivedPostRepository archivedPostRepository;

    public BatchConfig(PostRepository postRepository, ArchivedPostRepository archivedPostRepository) {
        this.postRepository = postRepository;
        this.archivedPostRepository = archivedPostRepository;
    }

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
    public Job helloJob(JobRepository jobRepository, Step helloStep) {
        return new JobBuilder("jobOne", jobRepository)
                .start(helloStep)
                .build();
    }

    @Bean
    public RepositoryItemReader<Post> printTitleToUppercaseItemReader() {
        return new RepositoryItemReaderBuilder<Post>()
                .name("printUpperCasePostReader")
                .repository(postRepository)
                .methodName("findAll")
                .pageSize(10)
                .sorts(Collections.singletonMap("id", Direction.ASC))
                .build();
    }

    @Bean
    public ItemProcessor<Post, String> printTitleToUppercaseProcessor() {
        return post -> {
            return post.getId().toString() + " " + post.getTitle().toUpperCase();
        };
    }

    @Bean
    public ItemWriter<String> printTitleToUppercaseItemWriter() {
        return chunk -> {
            for (String item : chunk) {
                System.out.println(item);
            }
        };
    }

    @Bean
    public Step printTitleToUppercaseStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          RepositoryItemReader<Post> printTitleToUppercaseItemReader,
                                          ItemProcessor<Post, String> printTitleToUppercaseProcessor,
                                          ItemWriter<String> printTitleToUppercaseItemWriter) {
        return new StepBuilder("printTitleToUppercaseStep", jobRepository)
                .<Post, String>chunk(10, transactionManager)
                .reader(printTitleToUppercaseItemReader)
                .processor(printTitleToUppercaseProcessor)
                .writer(printTitleToUppercaseItemWriter)
                .build();
    }

    @Bean
    public Job printTitleToUppercaseJob(JobRepository jobRepository, Step printTitleToUppercaseStep) {
        return new JobBuilder("printTitleToUppercaseJob", jobRepository)
                .start(printTitleToUppercaseStep)
                .build();
    }

    @Bean
    public RepositoryItemReader<Post> archivePostReader() {
        return new RepositoryItemReaderBuilder<Post>()
                .name("archivePostReader")
                .repository(postRepository)
                .methodName("findByCreatedAtBefore")
                .arguments(LocalDateTime.now().minusYears(1))
                .pageSize(500)
                .sorts(Collections.singletonMap("id", Direction.ASC))
                .build();
    }

    @Bean
    public ItemReader<Post> archivePostCustomReader(PostRepository postRepository) {
        return new NoOffsetItemReader(postRepository, LocalDateTime.now().minusYears(1), 500);
    }

    @Bean
    public ItemProcessor<Post, ArchivedPost> archivePostProcessor() {
        return post -> {
            return new ArchivedPost(post.getId(), post.getTitle(), post.getContent(),
                    post.getCreatedAt(), post.getUpdatedAt(),
                    LocalDateTime.now(), post.getAuthorId());
        };
    }

    @Bean
    public ItemWriter<ArchivedPost> archivePostWriter() {
        return chunk -> {
            List<? extends ArchivedPost> items = chunk.getItems();

            archivedPostRepository.saveAll(items);

            List<Long> originalIds = items.stream()
                    .map(ArchivedPost::getId)
                    .toList();
            postRepository.deleteAllByIdInBatch(originalIds);
        };
    }

    @Bean
    public Step archivePostStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                ItemReader<Post> archivePostCustomReader,
                                ItemProcessor<Post, ArchivedPost> archivePostProcessor,
                                ItemWriter<ArchivedPost> archivePostWriter) {
        return new StepBuilder("archivePostStep", jobRepository)
                .<Post, ArchivedPost>chunk(1000, transactionManager)
                .reader(archivePostCustomReader)
                .processor(archivePostProcessor)
                .writer(archivePostWriter)
                .build();
    }

    @Bean
    public Job archivePostJob(JobRepository jobRepository, Step archivePostStep) {
        return new JobBuilder("archivePostJob", jobRepository)
                .start(archivePostStep)
                .build();
    }
}
