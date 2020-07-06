import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.io.IOException;
import java.util.LinkedList;

public class Controller {
    @FXML
    Label userlabel;
    @FXML
    TextField messagefield;
    @FXML
    TextArea chatarea;
    @FXML
    ListView userlist;

    String ip,port,username;

    static Client client;
    UpdateCheckService service;
    public Controller(String ip,String port,String username){
        this.ip = ip;
        this.port = port;
        this.username = username;
    }

    public void initialize() throws IOException {
        System.out.println(ip+username);
        client = new Client(ip, username);
        userlabel.setText(username);
        client.runListenThread();
        service = new UpdateCheckService();
        service.setPeriod(Duration.millis(200));
        service.setRestartOnFailure(true);
        service.setOnSucceeded(e -> {
            if (service.getValue()){
                Message msg = client.inbox.poll();
                if(msg.type==AbstractHost.FLAG_MESSAGE) {
                    chatarea.appendText(msg.content + "\n");
                }
                if(msg.type==AbstractHost.FLAG_CLIENTS_LIST){
                    ObservableList<String> list = FXCollections.observableList(client.clientsList);
                    userlist.setItems(list);
                    userlist.refresh();
                }
            }
        });
        service.start();

    }

    public void textAction() {
        String message = messagefield.getText();
        client.sendMessage(client.os, message);
        System.out.println(client.toString() + ":" + message);
        chatarea.appendText("you : " + message + "\n");
        /*if (!client.inbox.isEmpty()) {
            chatarea.appendText(client.inbox.pop() + "\n");
        }*/
        messagefield.clear();

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
}
