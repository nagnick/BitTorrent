package BitTorrent;
import java.util.TimerTask;
public class UnchokeTimer extends TimerTask{
    Peer peerRunningTimer;
    boolean optimisticTimer;
    UnchokeTimer(Peer toTime, boolean optimistic){
        super();
        peerRunningTimer = toTime;
        optimisticTimer = optimistic;
    }
    @Override
    public void run() { // when timer up
        peerRunningTimer.timerUp(optimisticTimer);
    }
}
