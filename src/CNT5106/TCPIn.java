package CNT5106;

import java.io.ObjectInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;

public class TCPIn extends Thread{ // spinning thread waiting for peer messages
    LinkedBlockingQueue<Message> inbox;
    Socket connection;
    ObjectInputStream in;
     int peerID;
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
    public Message getHandShake(){ // use before starting thread and only once will fail if anyother message
        byte[] messageBytes = {0,0,0,0}; // puts a zero int in message if bellow fails
        try {
            messageBytes = in.readNBytes(32);
        }
        catch (Exception e) {
            System.out.println("Error reading TCPIn handshake");
        }
        return new Message(messageBytes, true,peerID); // peerID not used for handshake peerId in message is read instead
    }
    public void start(){
        try {
            // tcp network stuff
            while(!connection.isClosed()) { // testing required
                ByteBuffer lengthBuff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
                int messageLength = lengthBuff.put(in.readNBytes(4)).get(0);
                ByteBuffer message = ByteBuffer.allocate(5+messageLength).order(ByteOrder.BIG_ENDIAN);
                message.putInt(0,messageLength);
                message.put(4,in.readNBytes(1));
                message.put(5,in.readNBytes(messageLength));
                inbox.put(new Message(message.array(),false,peerID));
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
    public void setPeerId(int ID){
        peerID = ID;
    }
}
