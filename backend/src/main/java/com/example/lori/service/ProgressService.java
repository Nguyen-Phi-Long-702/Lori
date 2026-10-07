package com.example.lori.service;

import com.example.lori.dto.ProgressItemRequest;
import com.example.lori.dto.ProgressItemResponse;
import com.example.lori.dto.SyncProgressRequest;
import com.example.lori.dto.SyncProgressResponse;
import com.example.lori.repository.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Nghiep vu dong bo tien trinh hoc (/api/progress). */
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final UserProgressRepository userProgressRepository;

    /**
     * Dong bo tung muc theo quy tac: them moi, hoac ghi de neu lastStudiedAt moi hon ban tren server.
     * Ca lo nam trong 1 transaction: loi o bat ky muc nao thi khong muc nao duoc ghi.
     */
    @Transactional
    public SyncProgressResponse sync(UUID userId, SyncProgressRequest request) {
        int applied = 0;
        for (ProgressItemRequest item : request.items()) {
            applied += userProgressRepository.upsertIfNewer(
                    userId,
                    item.itemType(),
                    item.itemId(),
                    item.status(),
                    item.correctCount(),
                    item.incorrectCount(),
                    item.lastStudiedAt());
        }
        return new SyncProgressResponse(request.items().size(), applied);
    }

    /** Toan bo tien trinh cua user tren server. */
    @Transactional(readOnly = true)
    public List<ProgressItemResponse> pull(UUID userId) {
        return userProgressRepository.findByUserId(userId).stream()
                .map(progress -> new ProgressItemResponse(
                        progress.getItemType(),
                        progress.getItemId(),
                        progress.getStatus(),
                        progress.getCorrectCount(),
                        progress.getIncorrectCount(),
                        progress.getLastStudiedAt()))
                .toList();
    }
}