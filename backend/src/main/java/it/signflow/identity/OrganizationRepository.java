package it.signflow.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationRepository {
    private final JdbcClient jdbcClient;

    public OrganizationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<OptionResponse> partitions() {
        return options("select id, code, name, active from partitions order by code");
    }

    public List<OptionResponse> companies() {
        return options("select id, code, name, active from companies order by code");
    }

    public List<OptionResponse> roles() {
        return jdbcClient.sql("select id, code, description as name, true as active from roles order by code")
                .query(OptionResponse.class)
                .list();
    }

    public List<OptionResponse> groups() {
        return options("select id, code, name, active from user_groups order by code");
    }

    public boolean partitionExists(UUID id) {
        return exists("partitions", id);
    }

    public boolean companyExists(UUID id) {
        return exists("companies", id);
    }

    public boolean rolesExist(List<UUID> ids) {
        return allExist("roles", ids);
    }

    public boolean groupsExist(List<UUID> ids) {
        return allExist("user_groups", ids);
    }

    private List<OptionResponse> options(String sql) {
        return jdbcClient.sql(sql).query(OptionResponse.class).list();
    }

    private boolean exists(String table, UUID id) {
        Integer count = jdbcClient.sql("select count(*) from " + table + " where id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
        return count == 1;
    }

    private boolean allExist(String table, List<UUID> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        Integer count = jdbcClient.sql("select count(*) from " + table + " where id in (:ids)")
                .param("ids", ids)
                .query(Integer.class)
                .single();
        return count == ids.size();
    }
}
