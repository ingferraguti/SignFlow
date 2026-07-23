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

    public List<OrganizationItemResponse> organizationItems(String type) {
        String table = table(type);
        String partition = type.equals("partitions") ? "null::uuid" : "partition_id";
        return jdbcClient.sql("select id, code, name, active, " + partition + " as partition_id from " + table + " order by code")
                .query(OrganizationItemResponse.class).list();
    }

    public OrganizationItemResponse save(String type, UUID id, OrganizationItemRequest request) {
        String table = table(type);
        if (!type.equals("partitions") && request.partitionId() == null) {
            throw new IllegalArgumentException("Partition is required");
        }
        if (type.equals("partitions")) {
            jdbcClient.sql("""
                    insert into partitions (id, code, name, active) values (:id, :code, :name, :active)
                    on conflict (id) do update set code = excluded.code, name = excluded.name, active = excluded.active
                    """).param("id", id).param("code", request.code().toUpperCase())
                    .param("name", request.name().trim()).param("active", request.active()).update();
        } else {
            jdbcClient.sql("insert into " + table + " (id, code, name, partition_id, active) "
                            + "values (:id, :code, :name, :partitionId, :active) on conflict (id) do update set "
                            + "code = excluded.code, name = excluded.name, partition_id = excluded.partition_id, active = excluded.active")
                    .param("id", id).param("code", request.code().toUpperCase())
                    .param("name", request.name().trim()).param("partitionId", request.partitionId())
                    .param("active", request.active()).update();
        }
        return organizationItems(type).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public OrganizationItemResponse setActive(String type, UUID id, boolean active) {
        int updated = jdbcClient.sql("update " + table(type) + " set active = :active where id = :id")
                .param("active", active).param("id", id).update();
        if (updated == 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return organizationItems(type).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public boolean partitionExists(UUID id) {
        return exists("partitions", id);
    }

    public boolean companyExists(UUID id) {
        return exists("companies", id);
    }

    public boolean companyBelongsToPartition(UUID companyId, UUID partitionId) {
        Integer count = jdbcClient.sql("select count(*) from companies where id = :companyId and partition_id = :partitionId")
                .param("companyId", companyId).param("partitionId", partitionId).query(Integer.class).single();
        return count == 1;
    }

    public boolean rolesExist(List<UUID> ids) {
        return allExist("roles", ids);
    }

    public boolean groupsExist(List<UUID> ids) {
        return allExist("user_groups", ids);
    }

    public boolean groupsBelongToPartition(List<UUID> ids, UUID partitionId) {
        if (ids.isEmpty()) {
            return true;
        }
        Integer count = jdbcClient.sql("select count(*) from user_groups where id in (:ids) and partition_id = :partitionId")
                .param("ids", ids).param("partitionId", partitionId).query(Integer.class).single();
        return count == ids.size();
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

    private String table(String type) {
        return switch (type) {
            case "partitions" -> "partitions";
            case "companies" -> "companies";
            case "groups" -> "user_groups";
            default -> throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Unknown organization type");
        };
    }
}
