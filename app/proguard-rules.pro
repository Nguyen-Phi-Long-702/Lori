# Gson đọc/ghi JSON theo tên trường của DTO (gồm cả lớp lồng nhau) nên không được đổi tên hay xóa
-keep class com.example.lori.data.remote.dto.** { *; }

# WorkManager (androidx.work:work-runtime 2.9.1) — R1 bị văng NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> do AGP 9.1 strictFullModeForKeepRules
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }

# WorkManager (androidx.work:work-runtime 2.9.1) — R2 SyncWorker không chạy được và báo NoSuchMethodException: androidx.work.OverwritingInputMerger.<init> do AGP 9.1 strictFullModeForKeepRules
-keep class androidx.work.OverwritingInputMerger { <init>(); }

# Room (androidx.room:room-runtime 2.6.1) — R1 bị văng NoSuchMethodException: com.example.lori.data.local.AppDatabase_Impl.<init> do AGP 9.1 strictFullModeForKeepRules
-keep class com.example.lori.data.local.AppDatabase_Impl { <init>(); }
