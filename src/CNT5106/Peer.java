package CNT5106;
import CNT5106.Message;
public class Peer {

    public Peer(){
        // init stuff on creation
    }
    public boolean Connect(){
        // connect to other peers & main server
        // spin up several threads for each?
        return false; // failed to connect to peer network
    }
    public boolean getFile(){
        // start main process of asking peers for bytes of file
        // while loop
        return false; // failed to get all bytes of file from network
    }
    public static void main(String args[])
    {
        Peer me = new Peer();
        me.Connect();
        me.getFile();
    }
}
