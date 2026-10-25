package com.drs.core.rmi;

import com.drs.core.model.NodeStatus;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Ported from legacy {@code common.NodeService}: the single RMI contract used by Exp 1-3.
 * <p>
 * Exp 1  -> registerNode / getNodeState <br>
 * Exp 2  -> sendHeartbeat (processed on a thread pool + monitored for silence) <br>
 * Exp 3  -> getTime (Cristian's Algorithm time server)
 * <p>
 * The RMI registry (port 1099) is NOT started yet; that is a later task. For now the
 * Spring {@code CoordinatorService} holds the logic and this interface is the contract
 * the RMI wrapper will implement.
 */
public interface NodeService extends Remote {

    String registerNode(NodeStatus node) throws RemoteException;

    String getNodeState(String nodeId) throws RemoteException;

    String sendHeartbeat(String nodeId, long timestamp) throws RemoteException;

    long getTime() throws RemoteException;
}
