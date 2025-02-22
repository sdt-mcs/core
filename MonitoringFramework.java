/**
 * MonitoringFramework.java
 * 
 * This class implements the monitoring framework described in Section 4.1 of the paper.
 * It collects performance metrics, resource utilization and chain-level dynamics,
 * providing an adaptive sampling mechanism.
 */
package com.sdtmcs.monitoring;

import com.sdtmcs.model.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class MonitoringFramework {
    // System components to monitor
    private ServiceDependencyGraph dependencyGraph;
    private Map<String, EdgeNode> nodes;
    private Map<String, List<String>> serviceChains;
    
    // Monitoring state
    private Map<String, ServiceState> serviceStates;
    private Map<String, ChainState> chainStates;
    private Map<String, NodeState> nodeStates;
    
    // Monitoring data collection
    private AtomicReference<MonitoringData> latestData;
    private Queue<MonitoringData> dataHistory;
    private int historySize = 100;
    
    // Sampling parameters from Section 4.1
    private Map<String, Double> samplingIntervals;
    private double baseInterval = 1.0; // seconds
    private Map<String, Double> metricVariances;
    
    // Thresholds from Equation 45-47
    private double epsilonResource = 0.1;
    private double epsilonLatency = 0.05;
    private double utilizationThreshold = 0.8;
    private double latencyVarianceThreshold = 0.2;
    
    // Scheduler for periodic monitoring
    private ScheduledExecutorService monitorExecutor;
    private Map<String, ScheduledFuture<?>> monitoringTasks;
    
    /**
     * Constructor initializes the monitoring framework
     */
    public MonitoringFramework(ServiceDependencyGraph dependencyGraph,
                              Map<String, EdgeNode> nodes,
                              Map<String, List<String>> serviceChains) {
        this.dependencyGraph = dependencyGraph;
        this.nodes = nodes;
        this.serviceChains = serviceChains;
        
        // Initialize state tracking
        this.serviceStates = new ConcurrentHashMap<>();
        this.chainStates = new ConcurrentHashMap<>();
        this.nodeStates = new ConcurrentHashMap<>();
        
        // Initialize monitoring data
        this.latestData = new AtomicReference<>(new MonitoringData());
        this.dataHistory = new ConcurrentLinkedQueue<>();
        
        // Initialize sampling parameters
        this.samplingIntervals = new ConcurrentHashMap<>();
        this.metricVariances = new ConcurrentHashMap<>();
        
        // Initialize executor
        this.monitorExecutor = Executors.newScheduledThreadPool(2);
        this.monitoringTasks = new ConcurrentHashMap<>();
        
        // Initialize service states
        for (Microservice service : dependencyGraph.getAllServices()) {
            serviceStates.put(service.getId(), new ServiceState(service.getId()));
            samplingIntervals.put(service.getId(), baseInterval);
            metricVariances.put(service.getId(), 0.1);
        }
        
        // Initialize node states
        for (EdgeNode node : nodes.values()) {
            nodeStates.put(node.getId(), new NodeState(node.getId()));
            samplingIntervals.put("node_" + node.getId(), baseInterval);
            metricVariances.put("node_" + node.getId(), 0.1);
        }
        
        // Initialize chain states
        for (String chainId : serviceChains.keySet()) {
            chainStates.put(chainId, new ChainState(chainId, serviceChains.get(chainId)));
            samplingIntervals.put("chain_" + chainId, baseInterval);
            metricVariances.put("chain_" + chainId, 0.1);
        }
    }
    
    /**
     * Starts the monitoring framework
     */
    public void start() {
        // Start periodic collection of latest metrics
        monitoringTasks.put("global", monitorExecutor.scheduleAtFixedRate(
            this::collectAndAggregateMetrics, 0, 1, TimeUnit.SECONDS));
        
        // Start adaptive sampling for each entity
        for (String serviceId : serviceStates.keySet()) {
            scheduleAdaptiveSampling(serviceId);
        }
        
        for (String nodeId : nodeStates.keySet()) {
            scheduleAdaptiveSampling("node_" + nodeId);
        }
        
        for (String chainId : chainStates.keySet()) {
            scheduleAdaptiveSampling("chain_" + chainId);
        }
    }
    
    /**
     * Stops the monitoring framework
     */
    public void stop() {
        // Cancel all monitoring tasks
        for (ScheduledFuture<?> task : monitoringTasks.values()) {
            task.cancel(false);
        }
        
        // Shutdown executor
        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Schedules adaptive sampling for an entity
     * Implementation of Equation 45-46 from the paper
     */
    private void scheduleAdaptiveSampling(String entityId) {
        Runnable samplingTask = () -> {
            // Get current sampling interval
            double interval = samplingIntervals.get(entityId);
            
            // Collect detailed metrics for this entity
            collectEntityMetrics(entityId);
            
            // Calculate metric variance
            double variance = calculateMetricVariance(entityId);
            metricVariances.put(entityId, variance);
            
            // Calculate new sampling interval using Equation 45
            double epsilon = entityId.startsWith("node_") ? epsilonResource : epsilonLatency;
            double newInterval = baseInterval * Math.min(1.0, Math.sqrt(epsilon / Math.max(variance, 0.001)));
            
            // Apply constraints from Equation 46
            String slaType = getSLAType(entityId);
            double lSLA = getSLALatency(slaType);
            double tMin = 0.1; // Minimum 100ms interval
            
            double constrainedInterval = Math.min(lSLA / 10.0, Math.max(tMin, newInterval));
            samplingIntervals.put(entityId, constrainedInterval);
            
            // Reschedule with new interval
            if (monitoringTasks.containsKey(entityId)) {
                monitoringTasks.get(entityId).cancel(false);
            }
            
            monitoringTasks.put(entityId, monitorExecutor.schedule(
                () -> scheduleAdaptiveSampling(entityId),
                (long) (constrainedInterval * 1000),
                TimeUnit.MILLISECONDS));
        };
        
        // Schedule initial task
        double initialInterval = samplingIntervals.get(entityId);
        monitoringTasks.put(entityId, monitorExecutor.schedule(
            samplingTask, (long) (initialInterval * 1000), TimeUnit.MILLISECONDS));
    }
    
    /**
     * Gets SLA type for an entity
     */
    private String getSLAType(String entityId) {
        if (entityId.startsWith("chain_")) {
            return "chain";
        } else if (entityId.startsWith("node_")) {
            return "node";
        } else {
            return "service";
        }
    }
    
    /**
     * Gets SLA latency requirement based on entity type
     */
    private double getSLALatency(String slaType) {
        switch (slaType) {
            case "chain":
                return 500.0; // 500ms for end-to-end chain latency
            case "service":
                return 100.0; // 100ms for individual service
            case "node":
                return 1000.0; // 1s for node metrics
            default:
                return 100.0;
        }
    }
    
    /**
     * Collects and aggregates metrics from all monitored entities
     */
    private void collectAndAggregateMetrics() {
        // Create new monitoring data snapshot
        MonitoringData data = new MonitoringData();
        data.timestamp = System.currentTimeMillis();
        
        // Collect service metrics
        Map<String, ServiceMetrics> serviceMetrics = new HashMap<>();
        for (ServiceState state : serviceStates.values()) {
            String serviceId = state.getServiceId();
            serviceMetrics.put(serviceId, createServiceMetrics(serviceId));
        }
        data.serviceMetrics = serviceMetrics;
        
        // Collect node metrics
        Map<String, NodeMetrics> nodeMetrics = new HashMap<>();
        for (NodeState state : nodeStates.values()) {
            String nodeId = state.getNodeId();
            nodeMetrics.put(nodeId, createNodeMetrics(nodeId));
        }
        data.nodeMetrics = nodeMetrics;
        
        // Collect chain metrics
        Map<String, ChainMetrics> chainMetricsMap = new HashMap<>();
        for (ChainState state : chainStates.values()) {
            String chainId = state.getChainId();
            chainMetricsMap.put(chainId, createChainMetrics(chainId));
        }
        data.chainMetrics = chainMetricsMap;
        
        // Update latest data
        latestData.set(data);
        
        // Add to history
        dataHistory.add(data);
        while (dataHistory.size() > historySize) {
            dataHistory.poll();
        }
    }
    
    /**
     * Collects detailed metrics for a specific entity
     */
    private void collectEntityMetrics(String entityId) {
        if (entityId.startsWith("node_")) {
            String nodeId = entityId.substring(5);
            collectNodeMetrics(nodeId);
        } else if (entityId.startsWith("chain_")) {
            String chainId = entityId.substring(6);
            collectChainMetrics(chainId);
        } else {
            // Assume it's a service ID
            collectServiceMetrics(entityId);
        }
    }
    
    /**
     * Collects metrics for a specific service
     */
    private void collectServiceMetrics(String serviceId) {
        ServiceState state = serviceStates.get(serviceId);
        if (state == null) return;
        
        // Get the service and its node
        Microservice service = findServiceById(serviceId);
        if (service == null) return;
        
        String nodeId = service.getNodeId();
        EdgeNode node = nodeId != null ? nodes.get(nodeId) : null;
        
        if (node != null) {
            // Get resource utilization from the node
            ResourceCapacity capacity = node.getCapacity();
            
            // Update service state with new metrics
            state.updateCpuUtilization(capacity.getCpuUtilization());
            state.updateMemoryUtilization(capacity.getMemoryUtilization());
            state.updateBandwidthUtilization(capacity.getBandwidthUtilization());
            
            // In a real system, these would come from actual measurements
            // For simulation, we generate synthetic values
            state.updateProcessingLatency(
                generateSyntheticLatency(serviceId, capacity.getCpuUtilization()));
            state.updateQueueLength(
                generateSyntheticQueueLength(serviceId, capacity.getCpuUtilization()));
            state.updateRequestRate(
                generateSyntheticRequestRate(serviceId));
        }
    }
    
    /**
     * Collects metrics for a specific node
     */
    private void collectNodeMetrics(String nodeId) {
        NodeState state = nodeStates.get(nodeId);
        if (state == null) return;
        
        EdgeNode node = nodes.get(nodeId);
        if (node == null) return;
        
        // Get resource utilization from the node
        ResourceCapacity capacity = node.getCapacity();
        
        // Update node state with new metrics
        state.updateCpuUtilization(capacity.getCpuUtilization());
        state.updateMemoryUtilization(capacity.getMemoryUtilization());
        state.updateBandwidthUtilization(capacity.getBandwidthUtilization());
        
        // Calculate network latency (in a real system, this would be measured)
        double avgNetworkLatency = calculateAverageNetworkLatency(nodeId);
        state.updateNetworkLatency(avgNetworkLatency);
        
        // Calculate service density
        int serviceCount = countServicesOnNode(nodeId);
        state.updateServiceDensity(serviceCount);
    }
    
    /**
     * Collects metrics for a specific chain
     */
    private void collectChainMetrics(String chainId) {
        ChainState state = chainStates.get(chainId);
        if (state == null) return;
        
        List<String> serviceIds = serviceChains.get(chainId);
        if (serviceIds == null || serviceIds.isEmpty()) return;
        
        // Calculate end-to-end latency from service latencies
        double totalLatency = 0.0;
        double maxLatency = 0.0;
        double minLatency = Double.MAX_VALUE;
        
        for (String serviceId : serviceIds) {
            ServiceState serviceState = serviceStates.get(serviceId);
            if (serviceState != null) {
                double latency = serviceState.getProcessingLatency();
                totalLatency += latency;
                maxLatency = Math.max(maxLatency, latency);
                minLatency = Math.min(minLatency, latency);
            }
        }
        
        // Include communication latency between services
        double commLatency = calculateChainCommunicationLatency(chainId);
        totalLatency += commLatency;
        
        // Update chain state with new metrics
        state.updateEndToEndLatency(totalLatency);
        state.updateMaxServiceLatency(maxLatency);
        state.updateMinServiceLatency(minLatency);
        state.updateCommunicationLatency(commLatency);
        
        // Calculate chain completion rate (based on service request rates)
        double completionRate = calculateChainCompletionRate(chainId);
        state.updateCompletionRate(completionRate);
    }
    
    /**
     * Creates service metrics from current state
     */
    private ServiceMetrics createServiceMetrics(String serviceId) {
        ServiceState state = serviceStates.get(serviceId);
        if (state == null) {
            return new ServiceMetrics();
        }
        
        ServiceMetrics metrics = new ServiceMetrics();
        metrics.serviceId = serviceId;
        metrics.cpuUtilization = state.getCpuUtilization();
        metrics.memoryUtilization = state.getMemoryUtilization();
        metrics.bandwidthUtilization = state.getBandwidthUtilization();
        metrics.processingLatency = state.getProcessingLatency();
        metrics.queueLength = state.getQueueLength();
        metrics.requestRate = state.getRequestRate();
        metrics.latencyVariance = state.getLatencyVariance();
        
        return metrics;
    }
    
    /**
     * Creates node metrics from current state
     */
    private NodeMetrics createNodeMetrics(String nodeId) {
        NodeState state = nodeStates.get(nodeId);
        if (state == null) {
            return new NodeMetrics();
        }
        
        NodeMetrics metrics = new NodeMetrics();
        metrics.nodeId = nodeId;
        metrics.cpuUtilization = state.getCpuUtilization();
        metrics.memoryUtilization = state.getMemoryUtilization();
        metrics.bandwidthUtilization = state.getBandwidthUtilization();
        metrics.networkLatency = state.getNetworkLatency();
        metrics.serviceDensity = state.getServiceDensity();
        
        return metrics;
    }
    
    /**
     * Creates chain metrics from current state
     */
    private ChainMetrics createChainMetrics(String chainId) {
        ChainState state = chainStates.get(chainId);
        if (state == null) {
            return new ChainMetrics();
        }
        
        ChainMetrics metrics = new ChainMetrics();
        metrics.chainId = chainId;
        metrics.endToEndLatency = state.getEndToEndLatency();
        metrics.maxServiceLatency = state.getMaxServiceLatency();
        metrics.minServiceLatency = state.getMinServiceLatency();
        metrics.communicationLatency = state.getCommunicationLatency();
        metrics.completionRate = state.getCompletionRate();
        metrics.latencyVariance = state.getLatencyVariance();
        
        return metrics;
    }
    
    /**
     * Calculates metric variance for adaptive sampling
     */
    private double calculateMetricVariance(String entityId) {
        if (entityId.startsWith("node_")) {
            String nodeId = entityId.substring(5);
            NodeState state = nodeStates.get(nodeId);
            if (state != null) {
                return state.getCpuUtilizationVariance();
            }
        } else if (entityId.startsWith("chain_")) {
            String chainId = entityId.substring(6);
            ChainState state = chainStates.get(chainId);
            if (state != null) {
                return state.getLatencyVariance();
            }
        } else {
            // Assume it's a service ID
            ServiceState state = serviceStates.get(entityId);
            if (state != null) {
                return state.getLatencyVariance();
            }
        }
        
        return 0.1; // Default variance
    }
    
    /**
     * Calculates average network latency for a node
     */
    private double calculateAverageNetworkLatency(String nodeId) {
        EdgeNode node = nodes.get(nodeId);
        if (node == null) return 0.0;
        
        double totalLatency = 0.0;
        int count = 0;
        
        for (String otherNodeId : nodes.keySet()) {
            if (!otherNodeId.equals(nodeId)) {
                totalLatency += node.getNetworkDelay(otherNodeId);
                count++;
            }
        }
        
        return count > 0 ? totalLatency / count : 0.0;
    }
    
    /**
     * Counts services deployed on a node
     */
    private int countServicesOnNode(String nodeId) {
        int count = 0;
        
        for (Microservice service : dependencyGraph.getAllServices()) {
            if (nodeId.equals(service.getNodeId())) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Calculates communication latency for a chain
     */
    private double calculateChainCommunicationLatency(String chainId) {
        List<String> serviceIds = serviceChains.get(chainId);
        if (serviceIds == null || serviceIds.size() < 2) return 0.0;
        
        double totalLatency = 0.0;
        
        for (int i = 0; i < serviceIds.size() - 1; i++) {
            String sourceId = serviceIds.get(i);
            String targetId = serviceIds.get(i + 1);
            
            Microservice source = findServiceById(sourceId);
            Microservice target = findServiceById(targetId);
            
            if (source != null && target != null) {
                String sourceNodeId = source.getNodeId();
                String targetNodeId = target.getNodeId();
                
                if (sourceNodeId != null && targetNodeId != null) {
                    // If on same node, minimal communication latency
                    if (sourceNodeId.equals(targetNodeId)) {
                        totalLatency += 1.0; // 1ms local communication
                    } else {
                        // Otherwise, use network delay between nodes
                        EdgeNode sourceNode = nodes.get(sourceNodeId);
                        if (sourceNode != null) {
                            totalLatency += sourceNode.getNetworkDelay(targetNodeId);
                        } else {
                            totalLatency += 30.0; // Default 30ms delay
                        }
                    }
                }
            }
        }
        
        return totalLatency;
    }
    
    /**
     * Calculates chain completion rate
     */
    private double calculateChainCompletionRate(String chainId) {
        List<String> serviceIds = serviceChains.get(chainId);
        if (serviceIds == null || serviceIds.isEmpty()) return 0.0;
        
        // Use the minimum request rate as the chain completion rate
        double minRate = Double.MAX_VALUE;
        
        for (String serviceId : serviceIds) {
            ServiceState state = serviceStates.get(serviceId);
            if (state != null) {
                minRate = Math.min(minRate, state.getRequestRate());
            }
        }
        
        return minRate == Double.MAX_VALUE ? 0.0 : minRate;
    }
    
    /**
     * Finds a service by ID
     */
    private Microservice findServiceById(String serviceId) {
        for (Microservice service : dependencyGraph.getAllServices()) {
            if (service.getId().equals(serviceId)) {
                return service;
            }
        }
        return null;
    }
    
    /**
     * Generates synthetic latency for simulation
     */
    private double generateSyntheticLatency(String serviceId, double cpuUtilization) {
        // Base latency based on service ID hash
        double baseLatency = (Math.abs(serviceId.hashCode() % 10) + 5) * 5.0; // 25-75ms
        
        // Increase latency with CPU utilization (non-linear)
        double utilizationFactor = 1.0;
        if (cpuUtilization > 0.7) {
            utilizationFactor = 1.0 + Math.pow((cpuUtilization - 0.7) / 0.3, 2) * 5.0;
        }
        
        // Add some randomness
        double randomFactor = 0.9 + Math.random() * 0.2; // 0.9-1.1
        
        return baseLatency * utilizationFactor * randomFactor;
    }
    
    /**
     * Generates synthetic queue length for simulation
     */
    private double generateSyntheticQueueLength(String serviceId, double cpuUtilization) {
        // Base queue length
        double baseQueueLength = Math.abs(serviceId.hashCode() % 5) + 1; // 1-5
        
        // Increase queue with CPU utilization (exponential)
        double utilizationFactor = 1.0;
        if (cpuUtilization > 0.6) {
            utilizationFactor = Math.exp((cpuUtilization - 0.6) * 5.0);
        }
        
        // Add some randomness
        double randomFactor = 0.8 + Math.random() * 0.4; // 0.8-1.2
        
        return baseQueueLength * utilizationFactor * randomFactor;
    }
    
    /**
     * Generates synthetic request rate for simulation
     */
    private double generateSyntheticRequestRate(String serviceId) {
        // Base request rate based on service ID hash
        double baseRate = (Math.abs(serviceId.hashCode() % 20) + 5) * 2.0; // 10-50 req/s
        
        // Add time-based variation - simulate daily pattern
        long hourOfDay = (System.currentTimeMillis() / 3600000) % 24;
        double hourFactor = 0.7 + 0.6 * Math.sin(Math.PI * (hourOfDay - 6) / 12); // 0.7-1.3
        
        // Add some randomness
        double randomFactor = 0.9 + Math.random() * 0.2; // 0.9-1.1
        
        return baseRate * hourFactor * randomFactor;
    }
    
    /**
     * Gets the latest monitoring data
     */
    public MonitoringData getLatestData() {
        return latestData.get();
    }
    
    /**
     * Gets historical monitoring data
     */
    public List<MonitoringData> getHistoricalData() {
        return new ArrayList<>(dataHistory);
    }
    
    /**
     * Gets historical data for a specific service
     */
    public List<ServiceMetrics> getHistoricalServiceMetrics(String serviceId) {
        List<ServiceMetrics> metrics = new ArrayList<>();
        
        for (MonitoringData data : dataHistory) {
            ServiceMetrics serviceMetrics = data.serviceMetrics.get(serviceId);
            if (serviceMetrics != null) {
                metrics.add(serviceMetrics);
            }
        }
        
        return metrics;
    }
    
    /**
     * Updates the state of a service with new metrics
     */
    public void updateServiceMetrics(String serviceId, double cpuUtilization, 
                                   double memoryUtilization, double processingLatency,
                                   double queueLength, double requestRate) {
        ServiceState state = serviceStates.get(serviceId);
        if (state == null) return;
        
        state.updateCpuUtilization(cpuUtilization);
        state.updateMemoryUtilization(memoryUtilization);
        state.updateProcessingLatency(processingLatency);
        state.updateQueueLength(queueLength);
        state.updateRequestRate(requestRate);
    }
    
    /**
     * Inner class representing monitoring data snapshot
     */
    public static class MonitoringData {
        public long timestamp;
        public Map<String, ServiceMetrics> serviceMetrics;
        public Map<String, NodeMetrics> nodeMetrics;
        public Map<String, ChainMetrics> chainMetrics;
        
        public MonitoringData() {
            this.timestamp = System.currentTimeMillis();
            this.serviceMetrics = new HashMap<>();
            this.nodeMetrics = new HashMap<>();
            this.chainMetrics = new HashMap<>();
        }
    }
    
    /**
     * Inner class representing service metrics
     */
    public static class ServiceMetrics {
        public String serviceId;
        public double cpuUtilization;
        public double memoryUtilization;
        public double bandwidthUtilization;
        public double processingLatency;
        public double queueLength;
        public double requestRate;
        public double latencyVariance;
        
        public ServiceMetrics() {
            this.cpuUtilization = 0.0;
            this.memoryUtilization = 0.0;
            this.bandwidthUtilization = 0.0;
            this.processingLatency = 0.0;
            this.queueLength = 0.0;
            this.requestRate = 0.0;
            this.latencyVariance = 0.0;
        }
    }
    
    /**
     * Inner class representing node metrics
     */
    public static class NodeMetrics {
        public String nodeId;
        public double cpuUtilization;
        public double memoryUtilization;
        public double bandwidthUtilization;
        public double networkLatency;
        public int serviceDensity;
        
        public NodeMetrics() {
            this.cpuUtilization = 0.0;
            this.memoryUtilization = 0.0;
            this.bandwidthUtilization = 0.0;
            this.networkLatency = 0.0;
            this.serviceDensity = 0;
        }
    }
    
    /**
     * Inner class representing chain metrics
     */
    public static class ChainMetrics {
        public String chainId;
        public double endToEndLatency;
        public double maxServiceLatency;
        public double minServiceLatency;
        public double communicationLatency;
        public double completionRate;
        public double latencyVariance;
        
        public ChainMetrics() {
            this.endToEndLatency = 0.0;
            this.maxServiceLatency = 0.0;
            this.minServiceLatency = 0.0;
            this.communicationLatency = 0.0;
            this.completionRate = 0.0;
            this.latencyVariance = 0.0;
        }
    }
    
    /**
     * Inner class representing service state
     */
    private class ServiceState {
        private String serviceId;
        private double cpuUtilization;
        private double memoryUtilization;
        private double bandwidthUtilization;
        private double processingLatency;
        private double queueLength;
        private double requestRate;
        
        // Historical data for variance calculation
        private List<Double> latencyHistory;
        private int historyLimit = 20;
        
        public ServiceState(String serviceId) {
            this.serviceId = serviceId;
            this.cpuUtilization = 0.0;
            this.memoryUtilization = 0.0;
            this.bandwidthUtilization = 0.0;
            this.processingLatency = 0.0;
            this.queueLength = 0.0;
            this.requestRate = 0.0;
            this.latencyHistory = new ArrayList<>();
        }
        
        public void updateCpuUtilization(double value) {
            this.cpuUtilization = value;
        }
        
        public void updateMemoryUtilization(double value) {
            this.memoryUtilization = value;
        }
        
        public void updateBandwidthUtilization(double value) {
            this.bandwidthUtilization = value;
        }
        
        public void updateProcessingLatency(double value) {
            this.processingLatency = value;
            
            // Update history for variance calculation
            latencyHistory.add(value);
            while (latencyHistory.size() > historyLimit) {
                latencyHistory.remove(0);
            }
        }
        
        public void updateQueueLength(double value) {
            this.queueLength = value;
        }
        
        public void updateRequestRate(double value) {
            this.requestRate = value;
        }
        
        public String getServiceId() { return serviceId; }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getMemoryUtilization() { return memoryUtilization; }
        public double getBandwidthUtilization() { return bandwidthUtilization; }
        public double getProcessingLatency() { return processingLatency; }
        public double getQueueLength() { return queueLength; }
        public double getRequestRate() { return requestRate; }
        
        public double getLatencyVariance() {
            if (latencyHistory.size() < 2) return 0.0;
            
            // Calculate mean
            double sum = 0.0;
            for (double latency : latencyHistory) {
                sum += latency;
            }
            double mean = sum / latencyHistory.size();
            
            // Calculate variance
            double variance = 0.0;
            for (double latency : latencyHistory) {
                variance += Math.pow(latency - mean, 2);
            }
            variance /= latencyHistory.size();
            
            return Math.sqrt(variance) / mean; // Return normalized standard deviation
        }
    }
    
    /**
     * Inner class representing node state
     */
    private class NodeState {
        private String nodeId;
        private double cpuUtilization;
        private double memoryUtilization;
        private double bandwidthUtilization;
        private double networkLatency;
        private int serviceDensity;
        
        // Historical data for variance calculation
        private List<Double> cpuHistory;
        private int historyLimit = 20;
        
        public NodeState(String nodeId) {
            this.nodeId = nodeId;
            this.cpuUtilization = 0.0;
            this.memoryUtilization = 0.0;
            this.bandwidthUtilization = 0.0;
            this.networkLatency = 0.0;
            this.serviceDensity = 0;
            this.cpuHistory = new ArrayList<>();
        }
        
        public void updateCpuUtilization(double value) {
            this.cpuUtilization = value;
            
            // Update history for variance calculation
            cpuHistory.add(value);
            while (cpuHistory.size() > historyLimit) {
                cpuHistory.remove(0);
            }
        }
        
        public void updateMemoryUtilization(double value) {
            this.memoryUtilization = value;
        }
        
        public void updateBandwidthUtilization(double value) {
            this.bandwidthUtilization = value;
        }
        
        public void updateNetworkLatency(double value) {
            this.networkLatency = value;
        }
        
        public void updateServiceDensity(int value) {
            this.serviceDensity = value;
        }
        
        public String getNodeId() { return nodeId; }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getMemoryUtilization() { return memoryUtilization; }
        public double getBandwidthUtilization() { return bandwidthUtilization; }
        public double getNetworkLatency() { return networkLatency; }
        public int getServiceDensity() { return serviceDensity; }
        
        public double getCpuUtilizationVariance() {
            if (cpuHistory.size() < 2) return 0.0;
            
            // Calculate mean
            double sum = 0.0;
            for (double util : cpuHistory) {
                sum += util;
            }
            double mean = sum / cpuHistory.size();
            
            // Calculate variance
            double variance = 0.0;
            for (double util : cpuHistory) {
                variance += Math.pow(util - mean, 2);
            }
            variance /= cpuHistory.size();
            
            return variance;
        }
    }
    
    /**
     * Inner class representing chain state
     */
    private class ChainState {
        private String chainId;
        private List<String> serviceIds;
        private double endToEndLatency;
        private double maxServiceLatency;
        private double minServiceLatency;
        private double communicationLatency;
        private double completionRate;
        
        // Historical data for variance calculation
        private List<Double> latencyHistory;
        private int historyLimit = 20;
        
        public ChainState(String chainId, List<String> serviceIds) {
            this.chainId = chainId;
            this.serviceIds = serviceIds;
            this.endToEndLatency = 0.0;
            this.maxServiceLatency = 0.0;
            this.minServiceLatency = 0.0;
            this.communicationLatency = 0.0;
            this.completionRate = 0.0;
            this.latencyHistory = new ArrayList<>();
        }
        
        public void updateEndToEndLatency(double value) {
            this.endToEndLatency = value;
            
            // Update history for variance calculation
            latencyHistory.add(value);
            while (latencyHistory.size() > historyLimit) {
                latencyHistory.remove(0);
            }
        }
        
        public void updateMaxServiceLatency(double value) {
            this.maxServiceLatency = value;
        }
        
        public void updateMinServiceLatency(double value) {
            this.minServiceLatency = value;
        }
        
        public void updateCommunicationLatency(double value) {
            this.communicationLatency = value;
        }
        
        public void updateCompletionRate(double value) {
            this.completionRate = value;
        }
        
        public String getChainId() { return chainId; }
        public List<String> getServiceIds() { return serviceIds; }
        public double getEndToEndLatency() { return endToEndLatency; }
        public double getMaxServiceLatency() { return maxServiceLatency; }
        public double getMinServiceLatency() { return minServiceLatency; }
        public double getCommunicationLatency() { return communicationLatency; }
        public double getCompletionRate() { return completionRate; }
        
        public double getLatencyVariance() {
            if (latencyHistory.size() < 2) return 0.0;
            
            // Calculate mean
            double sum = 0.0;
            for (double latency : latencyHistory) {
                sum += latency;
            }
            double mean = sum / latencyHistory.size();
            
            // Calculate variance
            double variance = 0.0;
            for (double latency : latencyHistory) {
                variance += Math.pow(latency - mean, 2);
            }
            variance /= latencyHistory.size();
            
            return Math.sqrt(variance) / mean; // Return normalized standard deviation
        }
    }
}