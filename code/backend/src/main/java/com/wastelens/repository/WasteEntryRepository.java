package com.wastelens.repository;

import com.wastelens.domain.Category;
import com.wastelens.domain.Reason;
import com.wastelens.domain.Stage;
import com.wastelens.domain.WasteEntry;
import com.wastelens.web.dto.CategoryGramsDto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WasteEntryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<WasteEntry> rowMapper = (rs, rowNum) -> new WasteEntry(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("owner_id"),
        (UUID) rs.getObject("submission_id"),
        rs.getDate("entry_date").toLocalDate(),
        Category.fromValue(rs.getString("category")),
        rs.getInt("grams"),
        Stage.fromValue(rs.getString("stage")),
        Reason.fromValue(rs.getString("reason")),
        rs.getTimestamp("created_at").toInstant()
    );

    public WasteEntryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Acquires transaction-scoped advisory lock for the given owner (ADR-005).
     * Automatically released at transaction commit/rollback.
     */
    public void acquireOwnerAdvisoryLock(UUID ownerId) {
        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            ps -> ps.setString(1, ownerId.toString()),
            rs -> null
        );
    }

    /**
     * Looks up an existing entry by owner and submissionId.
     */
    public Optional<WasteEntry> findByOwnerAndSubmissionId(UUID ownerId, UUID submissionId) {
        String sql = """
            SELECT id, owner_id, submission_id, entry_date, category, grams, stage, reason, created_at
            FROM wastelens_private.waste_entry
            WHERE owner_id = ? AND submission_id = ?
            """;
        List<WasteEntry> results = jdbcTemplate.query(sql, rowMapper, ownerId, submissionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Returns total count of entries for an owner.
     */
    public long countByOwnerId(UUID ownerId) {
        String sql = "SELECT count(*) FROM wastelens_private.waste_entry WHERE owner_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, ownerId);
        return count != null ? count : 0L;
    }

    /**
     * Inserts new waste entry using ON CONFLICT DO NOTHING RETURNING ...
     * Returns empty Optional if unique constraint conflict occurred.
     */
    public Optional<WasteEntry> insert(WasteEntry entry) {
        String sql = """
            INSERT INTO wastelens_private.waste_entry (id, owner_id, submission_id, entry_date, category, grams, stage, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (owner_id, submission_id) DO NOTHING
            RETURNING id, owner_id, submission_id, entry_date, category, grams, stage, reason, created_at
            """;
        List<WasteEntry> results = jdbcTemplate.query(
            sql,
            rowMapper,
            entry.id(),
            entry.ownerId(),
            entry.submissionId(),
            Date.valueOf(entry.entryDate()),
            entry.category().getValue(),
            entry.grams(),
            entry.stage().getValue(),
            entry.reason().getValue(),
            Timestamp.from(entry.createdAt())
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public record WeekTotalAndCount(long totalGrams, long entryCount) {}

    /**
     * Aggregates total grams and entry count for a single week [monday, monday + 7).
     */
    public WeekTotalAndCount getWeekTotalAndCount(UUID ownerId, java.time.LocalDate monday) {
        String sql = """
            SELECT COALESCE(SUM(grams), 0) AS total_grams, COUNT(*) AS entry_count
            FROM wastelens_private.waste_entry
            WHERE owner_id = ? AND entry_date >= ? AND entry_date < ?
            """;
        return jdbcTemplate.queryForObject(
            sql,
            (rs, rowNum) -> new WeekTotalAndCount(
                rs.getLong("total_grams"),
                rs.getLong("entry_count")
            ),
            ownerId,
            Date.valueOf(monday),
            Date.valueOf(monday.plusDays(7))
        );
    }

    /**
     * Retrieves top 3 categories by total grams descending, with category name ascending tie-break.
     */
    public List<CategoryGramsDto> getWeekTopCategories(UUID ownerId, java.time.LocalDate monday) {
        String sql = """
            SELECT category, SUM(grams) AS total_grams
            FROM wastelens_private.waste_entry
            WHERE owner_id = ? AND entry_date >= ? AND entry_date < ?
            GROUP BY category
            ORDER BY total_grams DESC, category ASC
            LIMIT 3
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new CategoryGramsDto(
                rs.getString("category"),
                rs.getLong("total_grams")
            ),
            ownerId,
            Date.valueOf(monday),
            Date.valueOf(monday.plusDays(7))
        );
    }

    /**
     * Retrieves all entries for the owner ordered strictly by entryDate ASC, id ASC for export.
     */
    public List<WasteEntry> findAllByOwnerForExport(UUID ownerId) {
        String sql = """
            SELECT id, owner_id, submission_id, entry_date, category, grams, stage, reason, created_at
            FROM wastelens_private.waste_entry
            WHERE owner_id = ?
            ORDER BY entry_date ASC, id ASC
            """;
        return jdbcTemplate.query(sql, rowMapper, ownerId);
    }
}
