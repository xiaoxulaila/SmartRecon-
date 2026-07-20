package com.reconciliation.service;

import com.reconciliation.model.ReconRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存数据存储（共享给多个 Controller 使用）
 */
@Service
public class DataStoreService {

    private final Map<String, List<ReconRecord>> dataStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> metaStore = new ConcurrentHashMap<>();

    public void putData(String dataId, List<ReconRecord> records) {
        dataStore.put(dataId, records);
    }

    public List<ReconRecord> getData(String dataId) {
        return dataStore.getOrDefault(dataId, Collections.emptyList());
    }

    public void putMeta(String dataId, Map<String, Object> meta) {
        metaStore.put(dataId, meta);
    }

    public Map<String, Map<String, Object>> getMetaStore() {
        return metaStore;
    }

    public void remove(String dataId) {
        dataStore.remove(dataId);
        metaStore.remove(dataId);
    }

    public Map<String, List<ReconRecord>> getDataStore() {
        return dataStore;
    }
}
