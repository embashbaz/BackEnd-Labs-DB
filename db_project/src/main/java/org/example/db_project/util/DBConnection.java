package org.example.db_project.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {

    private static final String URL = "jdbc:postgresql://localhost:5432/smart_ecommerce";
    private static final String USER = "admin";
    private static final String PASSWORD = null;//"your_password_here";

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
