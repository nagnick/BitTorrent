package CNT5106;
import CNT5106.Message;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.ArrayList;

public class Peer {
    LinkedBlockingQueue<Message> inbox = new LinkedBlockingQueue<Message>(); // all recev tcp threads write to here
    ArrayList<TCPIn> peerInConnections = new ArrayList<TCPIn>(); // have these peers add messages to a thread safe queue
    ArrayList<TCPOut> peerOutConnections = new ArrayList<TCPOut>(); // peer connections to send messages
    public Peer(){
        // init stuff on creation
    }
    public boolean Connect(){
        // connect to other peers with help from main server
        new Thread(() -> { // start listening for peer connections on separate thread
            // spin up several threads for each peer that connects

        }).start();

        return false; // failed to connect to peer network
    }
    public boolean getFile(){
        // start main process of asking peers for bytes of file
        while(!inbox.isEmpty()){ // add && file is incomplete
            //process messages and respond appropriately
        }
        return false; // failed to get all bytes of file from network
    }
    public static void main(String args[])
    {
        Peer me = new Peer();
        me.Connect();
        me.getFile();
        Message myMessage = new Message(5, Message.MessageTypes.unchoke,"Hello");
        byte[] temp = myMessage.toBytes();
        System.out.println(Arrays.toString(temp));
        System.out.println(Arrays.toString(new Message(temp,false).toBytes()));
        System.out.println(Arrays.toString(temp = new Message(128).toBytes()));
        System.out.println(Arrays.toString(new Message(temp,true).toBytes()));
    }
}
