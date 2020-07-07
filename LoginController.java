import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class LoginController {
    @FXML
    TextField ipfield;
    @FXML
    TextField portfield;
    @FXML
    TextField userfield;
    @FXML
    Button connectbutton;
    Client client;
    public void initialize(){
        connectbutton.setOnAction(event -> {
            Controller controller = new Controller(ipfield.getText(),portfield.getText(),userfield.getText());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("rezo.fxml"));
            loader.setController(controller);
            try {
                Parent root = loader.load();
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                stage.setTitle("Client");
                /*Controller c = loader.getController();
                c.stage = stage;
                c.shareimg.setPickOnBounds(true);
                c.shareimg.setOnMouseClicked((MouseEvent e)->{
                    FileChooser fc = new FileChooser();
                    File file = fc.showOpenDialog(c.stage);
                });*/
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Stage login = (Stage) connectbutton.getScene().getWindow();
            login.close();

        });
    }
    /*private void loadSceneAndSendLogInfos(){
            FXMLLoader loader = new FXMLLoader(getClass().getResource("rezo.fxml"));
        try {
            Parent root = loader.load();
            Controller controller = (Controller) loader.getController();
            controller.transferMessage(client);
            System.out.println(ipfield.getText()+userfield.getText());
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Client");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }*/


}
