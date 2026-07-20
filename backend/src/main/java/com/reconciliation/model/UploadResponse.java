package com.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件上传响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    /** 数据源ID */
    private String dataId;
    /** 原始文件名 */
    private String fileName;
    /** 解析出的记录数 */
    private int recordCount;
    /** 表头列名 */
    private List<String> headers;
    /** 解析的预览数据（前20条） */
    private List<ReconRecord> previewData;
    /** 数据来源类型：excel / image / csv */
    private String sourceType;
}
