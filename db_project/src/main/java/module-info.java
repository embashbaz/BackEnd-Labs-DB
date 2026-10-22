module org.example.db_project {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.db_project to javafx.fxml;
    exports org.example.db_project;
}