package com.rag.knowledge.vector;

import com.rag.knowledge.config.PgVectorProperties;
import com.rag.knowledge.domain.entity.DocumentChunk;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PgVectorStoreService implements VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreService.class);

    private final PgVectorProperties properties;

    public PgVectorStoreService(PgVectorProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        initialize();
    }

    @Override
    public boolean available() {
        if (!properties.isEnabled()) {
            return false;
        }
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException exception) {
            log.warn("pgvector unavailable url={}", properties.getUrl(), exception);
            return false;
        }
    }

    @Override
    public String backendName() {
        return properties.isEnabled() ? "pgvector" : "local";
    }

    @Override
    public void initialize() {
        if (!properties.isEnabled() || !properties.isInitializeSchema()) {
            return;
        }
        String tableName = properties.safeTableName();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                      chunk_id BIGINT PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      kb_id BIGINT NOT NULL,
                      document_id BIGINT NOT NULL,
                      embedding_model VARCHAR(256),
                      embedding vector(%d) NOT NULL,
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """.formatted(tableName, properties.safeDimensions()));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_user_kb ON %s(user_id, kb_id)"
                    .formatted(tableName, tableName));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_user_kb_model ON %s(user_id, kb_id, embedding_model)"
                    .formatted(tableName, tableName));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_embedding ON %s USING hnsw (embedding vector_cosine_ops)"
                    .formatted(tableName, tableName));
        } catch (SQLException exception) {
            log.warn("Failed to initialize pgvector schema", exception);
        }
    }

    @Override
    public void upsert(DocumentChunk chunk, double[] embedding) {
        if (!properties.isEnabled() || embedding == null || embedding.length != properties.safeDimensions()) {
            return;
        }
        initialize();
        String sql = """
                INSERT INTO %s(chunk_id, user_id, kb_id, document_id, embedding_model, embedding, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::vector, now())
                ON CONFLICT (chunk_id)
                DO UPDATE SET
                  user_id = EXCLUDED.user_id,
                  kb_id = EXCLUDED.kb_id,
                  document_id = EXCLUDED.document_id,
                  embedding_model = EXCLUDED.embedding_model,
                  embedding = EXCLUDED.embedding,
                  updated_at = now()
                """.formatted(properties.safeTableName());
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chunk.getId());
            statement.setLong(2, chunk.getUserId());
            statement.setLong(3, chunk.getKbId());
            statement.setLong(4, chunk.getDocumentId());
            statement.setString(5, chunk.getEmbeddingModel());
            statement.setString(6, toVectorLiteral(embedding));
            statement.executeUpdate();
        } catch (SQLException exception) {
            log.warn("Failed to upsert pgvector chunkId={}", chunk.getId(), exception);
        }
    }

    @Override
    public int countDocumentVectors(Long userId, Long documentId, String embeddingModel) {
        return countCurrentModel(
                "SELECT COUNT(*) FROM %s WHERE user_id = ? AND document_id = ? AND embedding_model = ?"
                        .formatted(properties.safeTableName()),
                userId,
                documentId,
                embeddingModel
        );
    }

    @Override
    public int countKnowledgeBaseVectors(Long userId, Long kbId, String embeddingModel) {
        return countCurrentModel(
                "SELECT COUNT(*) FROM %s WHERE user_id = ? AND kb_id = ? AND embedding_model = ?"
                        .formatted(properties.safeTableName()),
                userId,
                kbId,
                embeddingModel
        );
    }

    @Override
    public void deleteDocument(Long userId, Long documentId) {
        delete("DELETE FROM %s WHERE user_id = ? AND document_id = ?".formatted(properties.safeTableName()), userId, documentId);
    }

    @Override
    public void deleteKnowledgeBase(Long userId, Long kbId) {
        delete("DELETE FROM %s WHERE user_id = ? AND kb_id = ?".formatted(properties.safeTableName()), userId, kbId);
    }

    private void delete(String sql, Long first, Long second) {
        if (!properties.isEnabled()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, first);
            statement.setLong(2, second);
            statement.executeUpdate();
        } catch (SQLException exception) {
            log.warn("Failed to delete pgvector rows", exception);
        }
    }

    private int count(String sql, Long first, Long second) {
        if (!properties.isEnabled()) {
            return 0;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, first);
            statement.setLong(2, second);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            log.warn("Failed to count pgvector rows", exception);
            return 0;
        }
    }

    private int countCurrentModel(String sql, Long first, Long second, String embeddingModel) {
        if (!properties.isEnabled()) {
            return 0;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, first);
            statement.setLong(2, second);
            statement.setString(3, embeddingModel);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            log.warn("Failed to count current pgvector rows", exception);
            return 0;
        }
    }

    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(properties.getUrl(), properties.getUsername(), properties.getPassword());
    }

    static String toVectorLiteral(double[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding[index]);
        }
        return builder.append(']').toString();
    }
}
