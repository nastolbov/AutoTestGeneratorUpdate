package ru.autotestgen.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setTitle("AutoTestGenerator - Генерация автотестов из XML-модели");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        boolean cliMode = false;
        for (String arg : args) {
            if ("--cli".equals(arg)) {
                cliMode = true;
                break;
            }
        }
        if (cliMode) {
            CliRunner.run(args);
        } else {
            launch(args);
        }
    }
}
