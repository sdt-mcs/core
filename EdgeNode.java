/**
 * EdgeNode.java
 * 
 * This class represents a compute node in the cloud-edge environment,
 * with its resource capacity and deployed services.
 */
package com.sdtmcs.model;

import java.util.*;

public class EdgeNode {
    private String id;
    private boolean isEdgeNode;  // true for edge node, false for cloud
    private ResourceCapacity capacity;
    private List<Microservice> deployedServices;
    private Map<String, Double> networkDelays;  // Delays to other nodes
    
    /**
     * Constructor initializes the edge node
     */
    public EdgeNode(String id, boolean isEdgeNode, ResourceCapacity capacity) {
        this.id = id;
        this.isEdgeNode = isEdgeNode;
        this.capacity = capacity;
        this.deployedServices = new ArrayList<>();
        this.networkDelays = new HashMap<>();
    }
    
    /**
     * Deploys a microservice on this node
     * 
     * @return true if deployment was successful, false if insufficient resources
     */
    public boolean deployService(Microservice service) {
        ResourceRequirements reqs = service.getBaseRequirements();
        if (capacity.allocate(reqs)) {
            deployedServices.add(service);
            service.setNodeId(id);
            return true;
        }
        return false;
    }
    
    /**
     * Removes a microservice from this node
     */
    public void removeService(Microservice service) {
        if (deployedServices.remove(service)) {
            capacity.release(service.getBaseRequirements());
            service.setNodeId(null);
        }
    }
    
    /**
     * Sets network delay to another node
     */
    public void setNetworkDelay(String targetNodeId, double delayMs) {
        networkDelays.put(targetNodeId, delayMs);
    }
    
    /**
     * Gets network delay to another node
     */
    public double getNetworkDelay(String targetNodeId) {
        return networkDelays.getOrDefault(targetNodeId, 100.0); // Default high delay
    }
    
    /**
     * Checks if a service is deployed on this node
     */
    public boolean hasService(String serviceId) {
        for (Microservice service : deployedServices) {
            if (service.getId().equals(serviceId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets a service deployed on this node by ID
     */
    public Microservice getService(String serviceId) {
        for (Microservice service : deployedServices) {
            if (service.getId().equals(serviceId)) {
                return service;
            }
        }
        return null;
    }
    
    /**
     * Updates the resource capacity of the node
     */
    public void updateCapacity(double cpuCapacity, double memoryCapacity, double bandwidthCapacity) {
        capacity.updateCapacity(cpuCapacity, memoryCapacity, bandwidthCapacity);
    }
    
    /**
     * Calculates the load factor of the node
     * This represents how heavily loaded the node is
     */
    public double calculateLoadFactor() {
        double cpuFactor = capacity.getCpuUtilization();
        double memoryFactor = capacity.getMemoryUtilization();
        double bandwidthFactor = capacity.getBandwidthUtilization();
        
        // Weighted average based on typical bottlenecks
        return cpuFactor * 0.5 + memoryFactor * 0.3 + bandwidthFactor * 0.2;
    }
    
    /**
     * Checks if the node is overloaded
     */
    public boolean isOverloaded() {
        return capacity.isOverloaded(0.8); // 80% threshold
    }
    
    /**
     * Calculates the resource interference level between two services
     * Implementation of Equation 20 from the paper
     */
    public double calculateInterference(String service1Id, String service2Id) {
        Microservice service1 = getService(service1Id);
        Microservice service2 = getService(service2Id);
        
        if (service1 == null || service2 == null) {
            return 0.0;
        }
        
        // Get resource utilization patterns
        Map<String, Double> util1 = service1.getResourceUtilization();
        Map<String, Double> util2 = service2.getResourceUtilization();
        
        // Calculate covariance of utilization patterns
        double covariance = calculateCovariance(util1, util2);
        
        // Calculate standard deviations
        double stdDev1 = calculateStdDev(util1);
        double stdDev2 = calculateStdDev(util2);
        
        // Calculate correlation coefficient (ρ in Equation 20)
        if (stdDev1 > 0 && stdDev2 > 0) {
            return covariance / (stdDev1 * stdDev2);
        } else {
            return 0.0;
        }
    }
    
    /**
     * Calculates covariance between two resource utilization patterns
     */
    private double calculateCovariance(Map<String, Double> util1, Map<String, Double> util2) {
        // For simplicity, we just use the current values
        // In a real implementation, this would use time series data
        
        double mean1 = calculateMean(util1);
        double mean2 = calculateMean(util2);
        
        // Calculate covariance using CPU, memory and bandwidth
        double cpuTerm = (util1.getOrDefault("cpu", 0.0) - mean1) * 
                       (util2.getOrDefault("cpu", 0.0) - mean2);
        double memTerm = (util1.getOrDefault("memory", 0.0) - mean1) * 
                       (util2.getOrDefault("memory", 0.0) - mean2);
        double bwTerm = (util1.getOrDefault("bandwidth", 0.0) - mean1) * 
                      (util2.getOrDefault("bandwidth", 0.0) - mean2);
        
        return (cpuTerm + memTerm + bwTerm) / 3.0;
    }
    
    /**
     * Calculates the mean of resource utilization values
     */
    private double calculateMean(Map<String, Double> util) {
        double sum = 0.0;
        for (double value : util.values()) {
            sum += value;
        }
        return sum / Math.max(1, util.size());
    }
    
    /**
     * Calculates standard deviation of resource utilization values
     */
    private double calculateStdDev(Map<String, Double> util) {
        double mean = calculateMean(util);
        double variance = 0.0;
        
        for (double value : util.values()) {
            variance += Math.pow(value - mean, 2);
        }
        
        variance /= Math.max(1, util.size());
        return Math.sqrt(variance);
    }
    
    /**
     * Estimates communication cost between this node and another for a service
     */
    public double estimateCommunicationCost(String serviceId, EdgeNode targetNode) {
        if (targetNode == null) {
            return Double.MAX_VALUE;
        }
        
        // If same node, minimal communication cost
        if (this.equals(targetNode)) {
            return 1.0;
        }
        
        // Get network delay to target node
        double delay = getNetworkDelay(targetNode.getId());
        
        // Get service data transfer volume (if available)
        Microservice service = getService(serviceId);
        double dataVolume = 1.0; // Default 1MB
        
        if (service != null) {
            // Assuming service has a method to get data transfer volume
            // This would come from dependency analysis
            dataVolume = estimateDataVolume(service);
        }
        
        // Communication cost = delay * data volume
        return delay * dataVolume;
    }
    
    /**
     * Estimates data volume for a service
     */
    private double estimateDataVolume(Microservice service) {
        // In practice, this would come from monitoring or profiling
        // For this implementation, we use a simplified estimate
        
        // Base volume based on service type
        double baseVolume = 1.0; // Default 1MB
        
        // Adjust based on resource requirements
        ResourceRequirements reqs = service.getBaseRequirements();
        if (reqs.getBandwidth() > 20.0) {
            // High bandwidth requirement suggests more data transfer
            baseVolume = 5.0; // 5MB
        } else if (reqs.getBandwidth() > 10.0) {
            baseVolume = 2.0; // 2MB
        }
        
        return baseVolume;
    }
    
    /**
     * Creates a string representation of the edge node
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EdgeNode{id='").append(id).append("', ");
        sb.append("type=").append(isEdgeNode ? "edge" : "cloud").append(", ");
        sb.append("services=").append(deployedServices.size()).append(", ");
        sb.append("capacity=").append(capacity).append("}");
        return sb.toString();
    }
    
    /**
     * Checks if this node equals another node
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdgeNode edgeNode = (EdgeNode) o;
        return id.equals(edgeNode.id);
    }
    
    /**
     * Generates hash code for the node
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    // Getters
    public String getId() { return id; }
    public boolean isEdgeNode() { return isEdgeNode; }
    public ResourceCapacity getCapacity() { return capacity; }
    public List<Microservice> getDeployedServices() { return new ArrayList<>(deployedServices); }
    public Map<String, Double> getNetworkDelays() { return new HashMap<>(networkDelays); }
    
    /**
     * Creates an edge node with typical edge capacity
     */
    public static EdgeNode createEdgeNode(String id) {
        return new EdgeNode(id, true, ResourceCapacity.createEdgeCapacity());
    }
    
    /**
     * Creates a cloud node with typical cloud capacity
     */
    public static EdgeNode createCloudNode(String id) {
        return new EdgeNode(id, false, ResourceCapacity.createCloudCapacity());
    }
}