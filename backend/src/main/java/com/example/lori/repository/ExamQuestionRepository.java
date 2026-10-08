package com.example.lori.repository;

import com.example.lori.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, UUID> {
    /** Toan bo cau hoi thuoc cac section da cho (khong dam bao thu tu; Service tu sap xep). */
    List<ExamQuestion> findBySectionIdIn(Collection<UUID> sectionIds);

    /** Dem so cau hoi theo tung de: moi dong la [paperId, soCau]. */
    @Query("""
            select s.paperId, count(q.id)
            from ExamQuestion q, ExamSection s
            where q.sectionId = s.id and s.paperId in :paperIds
            group by s.paperId
            """)
    List<Object[]> countByPaperIds(@Param("paperIds") Collection<UUID> paperIds);
}