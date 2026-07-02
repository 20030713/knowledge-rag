package com.rag.knowledge.service;

import com.rag.knowledge.dto.doc.DocumentResponse;
import com.rag.knowledge.dto.doc.DocumentBatchResponse;
import com.rag.knowledge.dto.doc.DocumentChunkResponse;
import com.rag.knowledge.dto.doc.DocumentIndexStatusResponse;
import com.rag.knowledge.dto.doc.DocumentParseResponse;
import com.rag.knowledge.dto.doc.DocumentQualityReportResponse;
import com.rag.knowledge.dto.doc.DocumentStatusResponse;
import com.rag.knowledge.dto.doc.DocumentSearchResultResponse;
import com.rag.knowledge.dto.doc.KnowledgeBaseIndexStatusResponse;
import com.rag.knowledge.dto.doc.VectorSyncResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    DocumentResponse upload(Long kbId, MultipartFile file);

    List<DocumentResponse> listMine(Long kbId);

    DocumentStatusResponse getStatus(Long id);

    DocumentParseResponse parse(Long id);

    DocumentBatchResponse batchParse(List<Long> ids);

    DocumentBatchResponse batchDelete(List<Long> ids);

    DocumentBatchResponse rebuildKnowledgeBase(Long kbId);

    List<DocumentChunkResponse> listChunks(Long id);

    List<DocumentSearchResultResponse> searchChunks(Long kbId, String keyword, Integer limit);

    DocumentQualityReportResponse qualityReport(Long kbId);

    DocumentIndexStatusResponse indexStatus(Long id);

    KnowledgeBaseIndexStatusResponse knowledgeBaseIndexStatus(Long kbId);

    VectorSyncResponse syncDocumentVectors(Long id);

    VectorSyncResponse syncKnowledgeBaseVectors(Long kbId);

    void delete(Long id);
}
