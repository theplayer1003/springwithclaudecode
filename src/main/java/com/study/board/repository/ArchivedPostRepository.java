package com.study.board.repository;

import com.study.board.entity.ArchivedPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchivedPostRepository extends JpaRepository<ArchivedPost, Long> {

}
