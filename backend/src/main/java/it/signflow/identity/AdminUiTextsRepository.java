package it.signflow.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminUiTextsRepository {
    private final JdbcClient jdbcClient;

    public AdminUiTextsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Map<String, String> findAll() {
        Map<String, String> texts = new LinkedHashMap<>();
        jdbcClient.sql("select text_key, text_value from admin_ui_texts order by text_key")
                .query((rs, rowNum) -> Map.entry(rs.getString("text_key"), rs.getString("text_value")))
                .list()
                .forEach(entry -> texts.put(entry.getKey(), entry.getValue()));
        return texts;
    }

    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        values.forEach((key, value) -> jdbcClient.sql("""
                        update admin_ui_texts set text_value = :value, updated_at = now() where text_key = :key
                        """)
                .param("key", key)
                .param("value", value.trim())
                .update());
        return findAll();
    }
}
