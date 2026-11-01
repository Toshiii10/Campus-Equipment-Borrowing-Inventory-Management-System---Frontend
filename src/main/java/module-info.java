module com.cebims {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    opens com.cebims to javafx.fxml, com.fasterxml.jackson.databind;
    exports com.cebims;
}