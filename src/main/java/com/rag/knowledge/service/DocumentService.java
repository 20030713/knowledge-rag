package com.rag.knowledge.service;

import com.rag.knowledge.dto.doc.DocumentResponse;
import com.rag.knowledge.dto.doc.DocumentStatusResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    DocumentResponse upload(Long kbId, MultipartFile file);

    List<DocumentResponse> listMine(Long kbId);

    DocumentStatusResponse getStatus(Long id);
}
