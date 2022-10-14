package CNT5106;

import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

public class TCPIn extends Thread{ // spinning thread waiting for peer messages
    LinkedBlockingQueue<Message> inbox;
    Socket connection;
    ObjectInputStream in;
    public TCPIn(LinkedBlockingQueue<Message> inbox, Socket connection){ // pass in peer info to form tcp connection
        this.inbox = inbox;
        this.connection = connection;
        try {
            in = new ObjectInputStream(connection.getInputStream());
        }
        catch(Exception e){
            System.out.println("Error creating TCPIN ");
        }
    }
    public Message getHandShake(){ // use before starting thread
        byte[] messageBytes = {0,0,0,0}; // puts a zero int in message if bellow fails
        try {
            messageBytes = in.readNBytes(10);
            // fix to read message properly
        }
        catch (Exception e) {
            System.out.println("Error reading TCPIn handshake");
        }
        return new Message(messageBytes, true);
    }
    public void start(){
        try {
            // tcp network stuff
            while(!connection.isClosed()) {
                byte[] message = in.readNBytes(10); // FIX to read bytes of a single message in not objects
                inbox.put(new Message(message,false));
            }
        }
        catch (Exception e) {
            System.out.println("Error running TCPIn thread");
        }
    }
    public void close(){
        try {
            connection.close();
            in.close();
        }
        catch (Exception e){
            System.out.println("Error closing TCP IN connection");
        }
    }
}
