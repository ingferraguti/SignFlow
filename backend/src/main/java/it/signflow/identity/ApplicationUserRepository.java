package it.signflow.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationUserRepository {
    private final JdbcClient jdbcClient;

    public ApplicationUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public PageResponse<ApplicationUserResponse> search(String query, Boolean active, int page, int size) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        String filter = """
                where (:query = ''
                   or lower(u.username) like '%' || :query || '%'
                   or lower(u.first_name) like '%' || :query || '%'
                   or lower(u.last_name) like '%' || :query || '%'
                   or lower(coalesce(u.fiscal_code, '')) like '%' || :query || '%')
                  and (:activeFilter = false or u.active = :active)
                """;
        long total = jdbcClient.sql("select count(*) from application_users u " + filter)
                .param("query", normalizedQuery)
                .param("activeFilter", active != null)
                .param("active", Boolean.TRUE.equals(active))
                .query(Long.class)
                .single();
        List<ApplicationUserResponse> items = jdbcClient.sql("""
                select u.*, p.code partition_code, p.name partition_name, p.active partition_active,
                       c.code company_code, c.name company_name, c.active company_active
                from application_users u
                join partitions p on p.id = u.partition_id
                join companies c on c.id = u.company_id
                """ + filter + " order by u.last_name, u.first_name limit :limit offset :offset")
                .param("query", normalizedQuery)
                .param("activeFilter", active != null)
                .param("active", Boolean.TRUE.equals(active))
                .param("limit", size)
                .param("offset", page * size)
                .query(this::mapUser)
                .list();
        return new PageResponse<>(items, page, size, total);
    }

    public Optional<ApplicationUserResponse> findById(UUID id) {
        return jdbcClient.sql("""
                select u.*, p.code partition_code, p.name partition_name, p.active partition_active,
                       c.code company_code, c.name company_name, c.active company_active
                from application_users u
                join partitions p on p.id = u.partition_id
                join companies c on c.id = u.company_id
                where u.id = :id
                """)
                .param("id", id)
                .query(this::mapUser)
                .optional();
    }

    public UUID create(ApplicationUserRequest request) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                insert into application_users (
                    id, username, oidc_subject, first_name, last_name, email, fiscal_code,
                    signer_fiscal_code, counter_signer_fiscal_code, active, partition_id, company_id
                ) values (:id, :username, :oidcSubject, :firstName, :lastName, :email, :fiscalCode,
                    :signerFiscalCode, :counterSignerFiscalCode, :active, :partitionId, :companyId)
                """)
                .param("id", id)
                .param("username", request.username())
                .param("oidcSubject", request.oidcSubject())
                .param("firstName", request.firstName())
                .param("lastName", request.lastName())
                .param("email", request.email())
                .param("fiscalCode", request.fiscalCode())
                .param("signerFiscalCode", request.signerFiscalCode())
                .param("counterSignerFiscalCode", request.counterSignerFiscalCode())
                .param("active", request.active())
                .param("partitionId", request.partitionId())
                .param("companyId", request.companyId())
                .update();
        replaceAssignments(id, request);
        return id;
    }

    public void update(UUID id, ApplicationUserRequest request) {
        jdbcClient.sql("""
                update application_users
                set username = :username,
                    oidc_subject = :oidcSubject,
                    first_name = :firstName,
                    last_name = :lastName,
                    email = :email,
                    fiscal_code = :fiscalCode,
                    signer_fiscal_code = :signerFiscalCode,
                    counter_signer_fiscal_code = :counterSignerFiscalCode,
                    active = :active,
                    partition_id = :partitionId,
                    company_id = :companyId,
                    updated_at = now()
                where id = :id
                """)
                .param("id", id)
                .param("username", request.username())
                .param("oidcSubject", request.oidcSubject())
                .param("firstName", request.firstName())
                .param("lastName", request.lastName())
                .param("email", request.email())
                .param("fiscalCode", request.fiscalCode())
                .param("signerFiscalCode", request.signerFiscalCode())
                .param("counterSignerFiscalCode", request.counterSignerFiscalCode())
                .param("active", request.active())
                .param("partitionId", request.partitionId())
                .param("companyId", request.companyId())
                .update();
        replaceAssignments(id, request);
    }

    public void setActive(UUID id, boolean active) {
        jdbcClient.sql("update application_users set active = :active, updated_at = now() where id = :id")
                .param("id", id)
                .param("active", active)
                .update();
    }

    private void replaceAssignments(UUID userId, ApplicationUserRequest request) {
        jdbcClient.sql("delete from application_user_roles where user_id = :userId").param("userId", userId).update();
        for (UUID roleId : request.roleIds() == null ? List.<UUID>of() : request.roleIds()) {
            jdbcClient.sql("insert into application_user_roles (user_id, role_id) values (:userId, :roleId)")
                    .param("userId", userId).param("roleId", roleId).update();
        }
        jdbcClient.sql("delete from application_user_groups where user_id = :userId").param("userId", userId).update();
        for (UUID groupId : request.groupIds() == null ? List.<UUID>of() : request.groupIds()) {
            jdbcClient.sql("insert into application_user_groups (user_id, group_id) values (:userId, :groupId)")
                    .param("userId", userId).param("groupId", groupId).update();
        }
    }

    private ApplicationUserResponse mapUser(ResultSet rs, int rowNum) throws SQLException {
        UUID userId = rs.getObject("id", UUID.class);
        return new ApplicationUserResponse(
                userId,
                rs.getString("username"),
                rs.getString("oidc_subject"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("fiscal_code"),
                rs.getString("signer_fiscal_code"),
                rs.getString("counter_signer_fiscal_code"),
                rs.getBoolean("active"),
                new OptionResponse(rs.getObject("partition_id", UUID.class), rs.getString("partition_code"), rs.getString("partition_name"), rs.getBoolean("partition_active")),
                new OptionResponse(rs.getObject("company_id", UUID.class), rs.getString("company_code"), rs.getString("company_name"), rs.getBoolean("company_active")),
                roles(userId),
                groups(userId));
    }

    private List<OptionResponse> roles(UUID userId) {
        return jdbcClient.sql("""
                select r.id, r.code, r.description as name, true as active
                from roles r join application_user_roles ur on ur.role_id = r.id
                where ur.user_id = :userId order by r.code
                """).param("userId", userId).query(OptionResponse.class).list();
    }

    private List<OptionResponse> groups(UUID userId) {
        return jdbcClient.sql("""
                select g.id, g.code, g.name, g.active
                from user_groups g join application_user_groups ug on ug.group_id = g.id
                where ug.user_id = :userId order by g.code
                """).param("userId", userId).query(OptionResponse.class).list();
    }
}
