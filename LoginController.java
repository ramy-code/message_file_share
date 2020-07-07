import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.File;
import java.io.IOException;

import static javafx.stage.StageStyle.UNDECORATED;

public class LoginController {
    @FXML
    TextField ipfield;
    @FXML
    TextField portfield;
    @FXML
    TextField userfield;
    @FXML
    Button connectbutton;
    @FXML
    CheckBox serverbox;
    Client client=null;
    Server server=null;
    public void initialize(){
        portfield.setText(String.valueOf(AbstractHost.DEFAULT_PORT));
        connectbutton.setOnAction(event -> {
            if(serverbox.isSelected()){
                try {
                    server = new Server(AbstractHost.DEFAULT_PORT);
                    ipfield.setText(server.localIP);
                    ipfield.commitValue();
                    server.waitForConnections();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Controller controller = new Controller(ipfield.getText(),portfield.getText(),userfield.getText());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("rezo.fxml"));
            loader.setController(controller);
            try {
                Parent root = loader.load();
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                stage.setTitle("Client");
                stage.setResizable(false);
                Controller c = loader.getController();
                if(server != null){
                    c.server = server;
                    c.headerlabel.setText(c.headerlabel.getText() + " [" + server.localIP+":"+AbstractHost.DEFAULT_PORT+"]");
                }
                stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                    public void handle(WindowEvent event) {
                        if(server != null){
                            //server.close();
                        }
                        if(client != null) {
                            client.close();
                        }
                        Platform.exit();
                        //System.exit(0);
                    }
                });
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Stage login = (Stage) connectbutton.getScene().getWindow();
            login.close();


        });
    }}
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



