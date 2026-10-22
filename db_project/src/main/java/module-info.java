module org.example.db_project {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens org.example.db_project to javafx.fxml;
    opens org.example.db_project.controller to javafx.fxml;
    opens org.example.db_project.model to javafx.base;
    exports org.example.db_project;
    exports org.example.db_project.controller;
    exports org.example.db_project.model;
}