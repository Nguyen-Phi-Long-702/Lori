package com.example.lori.service;

import com.example.lori.dto.ProgressItemRequest;
import com.example.lori.dto.ProgressItemResponse;
import com.example.lori.dto.SyncProgressRequest;
import com.example.lori.dto.SyncProgressResponse;
import com.example.lori.entity.UserProgress;
import com.example.lori.repository.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Nghiep vu dong bo tien trinh hoc (/api/progress). */
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final UserProgressRepository userProgressRepository;
    private final ExpService expService;

    /**
     * Dong bo tung muc theo quy tac: them moi, hoac ghi de neu lastStudiedAt moi hon ban tren server.
     * Muc nao duoc ghi thi duoc tinh EXP + streak (ExpService.awardForProgress).
     * Ca lo nam trong 1 transaction: loi o bat ky muc nao thi khong muc nao duoc ghi.
     */
    @Transactional
    public SyncProgressResponse sync(UUID userId, SyncProgressRequest request) {
        // lastStudiedAt hien co tren server cua tung muc, de ExpService biet muc do da duoc tinh EXP trong ngay chua
        Map<String, Instant> previousStudiedAt = new HashMap<>();
        for (UserProgress progress : userProgressRepository.findByUserId(userId)) {
            previousStudiedAt.put(progressKey(progress.getItemType(), progress.getItemId()), progress.getLastStudiedAt());
        }

        int applied = 0;
        for (ProgressItemRequest item : request.items()) {
            int written = userProgressRepository.upsertIfNewer(
                    userId,
                    item.itemType(),
                    item.itemId(),
                    item.status(),
                    item.correctCount(),
                    item.incorrectCount(),
                    item.lastStudiedAt());
            applied += written;
            if (written == 1) {
                String key = progressKey(item.itemType(), item.itemId());
                expService.awardForProgress(userId, item, previousStudiedAt.get(key));
                // Cap nhat de muc trung lap trong cung lo duoc so voi lan truoc cua chinh no
                previousStudiedAt.put(key, item.lastStudiedAt());
            }
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

    private static String progressKey(String itemType, int itemId) {
        return itemType + ":" + itemId;
    }
}