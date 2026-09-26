package com.temaconsulting.agente_iso.identity.infrastructure;

import com.temaconsulting.agente_iso.identity.domain.Role;
import com.temaconsulting.agente_iso.identity.domain.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
@Component
public class UserRowMapper implements RowMapper<User> {

    @Override
    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role").toUpperCase()),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
