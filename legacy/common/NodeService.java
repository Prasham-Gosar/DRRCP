package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The single RMI contract used by every experiment.
 * Exp 1  -> registerNode / getNodeState
 * Exp 2  -> sendHeartbeat  (processed on a thread pool + monitored for silence)
 * Exp 3  -> getTime        (Cristian's Algorithm time server)
 */
public interface NodeService extends Remote {
    String registerNode(NodeStatus node) throws RemoteException;

    String getNodeState(String nodeId) throws RemoteException;

    String sendHeartbeat(String nodeId, long timestamp) throws RemoteException;

    long getTime() throws RemoteException;
}
