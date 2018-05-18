package application;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class WebSocketChatStageController {
	private String user;
    private WebSocketClient webSocketClient;
	
	@FXML
    private TextField userTextField;
    @FXML
    private TextArea chatTextArea;
    @FXML
    private TextField messageTextField;
    @FXML
    private Button btnSet;
    @FXML
    private Button btnSend;
    @FXML
    private Button btnUpload;
    @FXML
    private Button btnDownload;
    
    @FXML
    private void initialize() {
    	webSocketClient = new WebSocketClient();
    	user = userTextField.getText();
    	btnSet.setDisable(true);
    	btnSend.setDisable(true);
    	btnUpload.setDisable(true);
    }
    
    @FXML
    private void btnSetLock() {
    	if(userTextField.getText().isEmpty()) btnSet.setDisable(true);
    	else btnSet.setDisable(false);
    }
    
    @FXML
    private void btnsSendUploadLock() {
    	btnSend.setDisable(false);
    	btnUpload.setDisable(false);
    }
    
    @FXML
    private void messageTextFieldClear() {
    	messageTextField.clear();
    }
    
    @FXML
    private void btnSet_Click() {
    	if(userTextField.getText().isEmpty()) 
    		return;
    	user = userTextField.getText();
    }
    
    @FXML
    private void btnSend_Click() {
    	if(!messageTextField.getText().isEmpty())
    		webSocketClient.sendMessage(messageTextField.getText());
    }
    
    @FXML
    private void btnUpload_Click() {
		try {
			webSocketClient.sendFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    @FXML
    private void btnDownload_Click() {
    	if(webSocketClient.checkAttachment())
			try {
				webSocketClient.downloadAttachment();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	else {
    		Alert alert = new Alert(AlertType.INFORMATION);
    		alert.setTitle("Attachment information");
    		alert.setHeaderText(null);
    		alert.setContentText("No attachment sent yet");

    		alert.showAndWait();
    	}

    }
    
    
	public void closeSession(CloseReason closeReason) {
		try {
			webSocketClient.session.close(closeReason);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	@ClientEndpoint
	public class WebSocketClient{
		private Session session;
		private ByteBuffer lastAttachment = null;
		
		public WebSocketClient() {
			connectToWebSocket();
		}
		
		@OnOpen
		public void onOpen(Session session) {
			System.out.println("Connection is opened.");
			this.session = session;
		}
		
		@OnClose
		public void onClose(CloseReason closeReason) {
			System.out.println("Connection is closed: " + closeReason.getReasonPhrase());
		}
		
		@OnError
		public void onError(Throwable throwable) {
			System.out.println("Error occured");
			throwable.printStackTrace();
		}
		
		@OnMessage
		public void onMessage(String message, Session session) {
			System.out.println("Message was received");
			chatTextArea.setText(chatTextArea.getText() + message + "\n");
		}
		
		@OnMessage
		public void onMessage(ByteBuffer byteBuffer, Session session) {
			System.out.println("File was received");
			
			lastAttachment = ByteBuffer.allocateDirect(byteBuffer.capacity());
			byteBuffer.rewind();
			lastAttachment.put(byteBuffer);
			lastAttachment.flip();
			byteBuffer.clear();
			
			chatTextArea.setText(chatTextArea.getText() + "Attachment sent\n");
		}
		
		public boolean checkAttachment() {
			if(lastAttachment != null && lastAttachment.capacity() != 0)
				return true;
			else return false;
		}
		
		private void connectToWebSocket() {
			WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
			try {
				URI uri = URI.create("ws://localhost:8080/Chat/WebSocketEndpoint");
				webSocketContainer.connectToServer(this, uri);
			} catch(DeploymentException | IOException e) {
				e.printStackTrace();
			}
		}
		
		public void sendMessage(String message) {
			try {
				System.out.println("Message was sent: " + message);
				session.getBasicRemote().sendText(user + ": " + message);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		
		public void downloadAttachment() throws IOException {
			
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Zapisz plik");
			Window stage = null;
			File outFile = fileChooser.showSaveDialog(stage);
			
			if(outFile == null) return;
			
			FileChannel channel;
			FileOutputStream fileOutStream = new FileOutputStream(outFile);
			channel = fileOutStream.getChannel();
			channel.write(lastAttachment);
			channel.close();
			fileOutStream.close();
			chatTextArea.setText(chatTextArea.getText() + "Attachment downloaded\n");
		}
		@FXML
	    private void sendFile() throws IOException {
	    	RandomAccessFile file = null;
	    	
	    	FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Wybierz plik");
			Window stage = null;
			File selectedFile = fileChooser.showOpenDialog(stage);
			
			if(selectedFile == null) return;
	    	try {
				file = new RandomAccessFile(selectedFile.getAbsolutePath(), "r");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			FileChannel channel = file.getChannel();
			ByteBuffer buffer = ByteBuffer.allocate((int)file.length());
			try {
				while (channel.read(buffer) > 0) {
					buffer.rewind();
					session.getBasicRemote().sendBinary(buffer);
					buffer.clear(); 
					buffer.flip();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			chatTextArea.setText(chatTextArea.getText() + "Attachment sent\n");
			try {
				channel.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	    }
		
	}
}
