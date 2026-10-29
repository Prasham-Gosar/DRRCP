package com.drs.core.rmi;

import com.drs.core.model.NodeStatus;
import com.drs.core.service.CoordinatorService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class NodeServiceImpl extends UnicastRemoteObject implements NodeService {

    private static final long serialVersionUID = 1L;

    private final CoordinatorService coordinator;

    public NodeServiceImpl(CoordinatorService coordinator) throws RemoteException {
        super();
        this.coordinator = coordinator;
    }

    @Override
    public String registerNode(NodeStatus node) throws RemoteException {
        var result = coordinator.registerNode(
                node.getNodeId(),
                node.getBatteryLevel(),
                node.getSignalStrength(),
                node.getNodeType(),
                node.getClusterId());
        return "Registered " + result.nodeId() + " — score " + result.connectivityScore()
               + " (" + result.state() + ")";
    }

    @Override
    public String getNodeState(String nodeId) throws RemoteException {
        String state = coordinator.getNodeState(nodeId);
        return state != null ? state : "UNKNOWN";
    }

    @Override
    public String sendHeartbeat(String nodeId, long timestamp) throws RemoteException {
        var result = coordinator.sendHeartbeat(nodeId, timestamp);
        return "Heartbeat processed on [" + result.thread() + "]";
    }

    @Override
    public long getTime() throws RemoteException {
        return coordinator.getTime().serverTime();
    }
}