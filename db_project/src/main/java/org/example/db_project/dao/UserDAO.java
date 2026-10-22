package org.example.db_project.dao;


import org.example.db_project.model.User;
import org.example.db_project.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    public List<User> findCustomers() throws SQLException {
        String sql = "SELECT user_id, full_name, email, role FROM users "
                   + "WHERE role = 'CUSTOMER' ORDER BY full_name";
        List<User> users = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new User(
                        rs.getInt("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("role")));
            }
        }
        return users;
    }
}
