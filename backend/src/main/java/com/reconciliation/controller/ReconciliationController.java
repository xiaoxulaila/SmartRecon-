package com.reconciliation.controller;

import com.reconciliation.dto.ReconRequest;
import com.reconciliation.model.ReconRecord;
import com.reconciliation.model.ReconResult;
import com.reconciliation.service.DataStoreService;
import com.reconciliation.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 对账操作控制器
 */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationService reconciliationService;
    private final DataStoreService dataStoreService;

    public ReconciliationController(ReconciliationService reconciliationService,
                                     DataStoreService dataStoreService) {
        this.reconciliationService = reconciliationService;
        this.dataStoreService = dataStoreService;
    }

    /**
     * 执行对账（支持新版多列匹配 + 旧版兼容）
     */
    @PostMapping("/run")
    public ReconResult runReconciliation(@RequestBody ReconRequest request) {
        log.info("开始对账: source={}, target={}, matchMode={}, matchColumns={}",
                request.getSourceDataId(), request.getTargetDataId(),
                request.getMatchMode(), request.getMatchColumns() != null ? request.getMatchColumns().size() : 0);

        List<ReconRecord> sourceData = dataStoreService.getData(request.getSourceDataId());
        List<ReconRecord> targetData = dataStoreService.getData(request.getTargetDataId());

        if (sourceData == null || sourceData.isEmpty()) {
            throw new IllegalArgumentException("数据源A未找到或为空");
        }
        if (targetData == null || targetData.isEmpty()) {
            throw new IllegalArgumentException("数据源B未找到或为空");
        }

        // 新版：多列匹配
        if (request.getMatchColumns() != null && !request.getMatchColumns().isEmpty()) {
            log.info("使用多列匹配模式，共 {} 列", request.getMatchColumns().size());
            return reconciliationService.reconcileWithColumns(
                    sourceData, targetData,
                    request.getMatchColumns(),
                    request.getMatchMode()
            );
        }

        // 旧版兼容：固定金额+日期匹配
        if (request.getSourceAmountColumn() != null || request.getTargetAmountColumn() != null) {
            remapFields(request, sourceData, targetData);
        }

        BigDecimal tolerance = request.getAmountTolerance() != null
                ? request.getAmountTolerance() : null;
        Integer dateTolerance = request.getDateToleranceDays() != null
                ? request.getDateToleranceDays() : null;

        return reconciliationService.reconcile(sourceData, targetData, tolerance, dateTolerance);
    }

    private void remapFields(ReconRequest request, List<ReconRecord> sourceData, List<ReconRecord> targetData) {
        if (request.getSourceAmountColumn() != null) {
            for (ReconRecord r : sourceData) {
                if (r.getRawData() != null) {
                    String val = r.getRawData().get(request.getSourceAmountColumn());
                    if (val != null) {
                        try {
                            r.setAmount(new BigDecimal(val.replace(",", "").trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (request.getSourceDateColumn() != null) {
                for (ReconRecord r : sourceData) {
                    if (r.getRawData() != null) {
                        r.setDate(r.getRawData().get(request.getSourceDateColumn()));
                    }
                }
            }
        }
        if (request.getTargetAmountColumn() != null) {
            for (ReconRecord r : targetData) {
                if (r.getRawData() != null) {
                    String val = r.getRawData().get(request.getTargetAmountColumn());
                    if (val != null) {
                        try {
                            r.setAmount(new BigDecimal(val.replace(",", "").trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (request.getTargetDateColumn() != null) {
                for (ReconRecord r : targetData) {
                    if (r.getRawData() != null) {
                        r.setDate(r.getRawData().get(request.getTargetDateColumn()));
                    }
                }
            }
        }
    }
}
