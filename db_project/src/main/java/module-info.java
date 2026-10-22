module org.example.db_project {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens org.example.db_project to javafx.fxml;
    exports org.example.db_project;
}