import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller {
    @FXML
    Label userlabel;
    @FXML
    TextField messagefield;
    @FXML
    TextArea chatarea;
    @FXML
    ListView userlist;
    @FXML
    ImageView shareimg;
    @FXML
    AnchorPane ap;
    @FXML
    Button buttonclose;
    @FXML
    Label headerlabel;

    Stage stage;

    String ip,port,username;
    File file;


    static Client client;
    Server server = null;
    UpdateCheckService service;
    public Controller(){}
    public Controller(String ip,String port,String username){
        this.ip = ip;
        this.port = port;
        this.username = username;
    }
    public void pickFile(){
        FileChooser fc = new FileChooser();
        this.stage = (Stage) ap.getScene().getWindow();
        File file = fc.showOpenDialog(stage);
        client.sendFile(file.getPath());
    }

    public void initialize() throws IOException {
        System.out.println(ip+username);
        //Stage stage = (Stage) ap.getScene().getWindow();
        client = new Client(ip, Integer.valueOf(port),username);
        userlabel.setText(username);
        client.runListenThread();
        service = new UpdateCheckService();
        service.setPeriod(Duration.millis(100));
        service.setRestartOnFailure(true);
        service.setOnSucceeded(e -> {
                if(!client.inbox.isEmpty()){
                Message msg = client.inbox.poll();
                if(msg.type==AbstractHost.FLAG_MESSAGE) {
                    chatarea.appendText(msg.content + "\n");
                }
                if(msg.type==AbstractHost.FLAG_CLIENTS_LIST){
                    ObservableList<String> list = FXCollections.observableList(client.clientsList);
                    //System.out.println(list);
                    userlist.setItems(list);
                    userlist.refresh();
                }
                if(msg.type==AbstractHost.FLAG_FILE_SEND_RQST){
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Confirmation Dialog");
                    alert.setHeaderText("You're going to receive a file");
                    alert.setContentText(msg.content);

                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.get() == ButtonType.OK){
                        client.acceptFileTransfert();
                    } else {
                        client.refuseFileTransfert();
                    }
                }
                if(msg.type==AbstractHost.FLAG_FILE){
                    //choisir le dossier de destination
                    FileChooser fc = new FileChooser();
                    //appel receive file avec la taille
                    String contenu = msg.content;
                    int length = Integer.valueOf(contenu.substring(contenu.indexOf("|") + 1));
                    byte[] data = new byte[0];
                    try {
                        data = client.receiveFile(client.is, length);
                    } catch (IOException ex) {
                        System.err.println(ex);
                    }
                    String fileName = contenu.substring(0, contenu.indexOf("|"));
                    fc.setInitialFileName(fileName);
                    File recu = fc.showSaveDialog(stage);
                    //appel save file avec dossier de destination
                    client.saveFile(recu.getPath(), data);
                }
                }
        });
        service.start();

    }


    public void textAction() {
        String message = messagefield.getText();
        if (message.charAt(0) == '@'){
            Matcher m = Pattern.compile("@(\\w+?)\\s+(.+)").matcher(message);
            if(m.find()) {
                String dest = m.group(1);
                String txt = m.group(2);
                try {
                    client.sendPrivateMessage(client.os, dest, txt);
                    chatarea.appendText("Private message to [" + dest + "]: " + txt);
                    messagefield.clear();
                } catch (Exception e) {
                    chatarea.appendText("[Message not sent] : User not found or offline");
                }
            }
        }
        else {
            client.sendMessage(client.os, message);
            System.out.println(client.toString() + ":" + message);
            chatarea.appendText("you : " + message + "\n");
        /*if (!client.inbox.isEmpty()) {
            chatarea.appendText(client.inbox.pop() + "\n");
        }*/
            messagefield.clear();
        }
    }


    private static class UpdateCheckService extends ScheduledService<Boolean> {

        @Override
        protected Task<Boolean> createTask() {
            return new Task<Boolean>() {

                @Override
                protected Boolean call() throws Exception {
                    client.requestClientsList();
                    if(!client.inbox.isEmpty()){

                        return true;
                    }
                    return false;
                }
            };
        }
    }
    public void transferMessage(String ip,String port,String user){
        this.ip = ip;
        this.username = user;
        this.port = port;
    }
    public void disconnect(){
        client.close();
        if(server != null)
            server.close();
        Platform.exit();
    }
}
