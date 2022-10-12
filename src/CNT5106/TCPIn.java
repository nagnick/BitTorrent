package CNT5106;

import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

public class TCPIn extends Thread{ // spining thread waiting for peer messages
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
    public void run(){
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
}
