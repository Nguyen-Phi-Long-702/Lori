package com.example.lori.data.remote.dto;

import java.util.List;

//Body của POST /api/progress/sync (tối đa 500 mục/lần)
public class SyncProgressRequest {
    public final List<ProgressItemRequest> items;

    public SyncProgressRequest(List<ProgressItemRequest> items) {
        this.items = items;
    }
}