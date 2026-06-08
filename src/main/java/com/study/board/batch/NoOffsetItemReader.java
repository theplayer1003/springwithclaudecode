package com.study.board.batch;

import com.study.board.entity.Post;
import com.study.board.repository.PostRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;

public class NoOffsetItemReader implements ItemReader<Post> {

    private final PostRepository postRepository;
    private final LocalDateTime cutoff;
    private final int pageSize;

    private Long lastSeenId = 0L;
    private Iterator<Post> currentPageIterator = Collections.emptyIterator();

    public NoOffsetItemReader(PostRepository postRepository, LocalDateTime cutoff, int pageSize) {
        this.postRepository = postRepository;
        this.cutoff = cutoff;
        this.pageSize = pageSize;
    }

    @Nullable
    @Override
    public Post read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {

        if (!currentPageIterator.hasNext()) {
            fetchNextPage();
        }

        if (!currentPageIterator.hasNext()) {
            return null;
        }

        Post nextPost = currentPageIterator.next();

        this.lastSeenId = nextPost.getId();

        return nextPost;
    }

    private void fetchNextPage() {
        PageRequest pageRequest = PageRequest.of(0, pageSize);

        List<Post> posts = postRepository.findByCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
                cutoff, lastSeenId, pageRequest);

        if (posts != null && !posts.isEmpty()) {
            this.currentPageIterator = posts.iterator();
        } else {
            this.currentPageIterator = Collections.emptyIterator();
        }
    }

}
