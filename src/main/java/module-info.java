module net.uberfoo.ai.ralphy {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.core;

    opens net.uberfoo.ai.ralphy to javafx.fxml, spring.beans, spring.context, spring.core;
    exports net.uberfoo.ai.ralphy;
}
