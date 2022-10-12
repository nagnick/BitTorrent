package CNT5106;
import CNT5106.Message;

import java.util.ArrayList;

public class Peer {
    ArrayList<TCPIn> peerInConnections = new ArrayList<TCPIn>(); // have these peers add messages to a thread safe queue
    ArrayList<TCPOut> peerOutConnections = new ArrayList<TCPOut>(); // peer connections to send messages
    public Peer(){
        // init stuff on creation
    }
    public boolean Connect(){
        // connect to other peers & main server
        // spin up several threads for each peer
        return false; // failed to connect to peer network
    }
    public boolean getFile(){
        // start main process of asking peers for bytes of file
        // while loop over Inqueue
        return false; // failed to get all bytes of file from network
    }
    public static void main(String args[])
    {
        Peer me = new Peer();
        me.Connect();
        me.getFile();
    }
}
