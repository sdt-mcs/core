/**
 * Microservice.java
 * 
 * This class represents a microservice in the SDT-MCS framework.
 * Each microservice has baseline resource requirements and runtime properties
 * that affect its placement and scheduling decisions.
 */
package com.sdtmcs.model;

import java.util.HashMap;
import java.util.Map;

public class Microservice {
    private String id;
    private String nodeId;  // ID of the node where the service is deployed
    private ResourceRequirements baseRequirements;
    private double executionTime;
    private double serviceRate;  // µi in the paper
    private Map<String, Double> resourceUtilization;  // Current utilization
    
    public Microservice(String id, ResourceRequirements baseRequirements, double executionTime) {
        this.id = id;
        this.baseRequirements = baseRequirements;
        this.executionTime = executionTime;
        this.serviceRate = 1.0 / executionTime;  // Default service rate
        this.resourceUtilization = new HashMap<>();
        this.resourceUtilization.put("cpu", 0.0);
        this.resourceUtilization.put("memory", 0.0);
        this.resourceUtilization.put("bandwidth", 0.0);
    }
    
    /**
     * Calculates dynamic resource requirements based on current workload
     * 
     * @param requestRate Current request rate (λ)
     * @return Adjusted resource requirements
     */
    public ResourceRequirements getDynamicRequirements(double requestRate) {
        double loadFactor = calculateWorkloadDynamics(requestRate);
        double utilizationFactor = calculateUtilizationImpact();
        
        // Create new requirements with dynamic adjustments
        ResourceRequirements dynamicReqs = new ResourceRequirements(
            baseRequirements.getCpu() * loadFactor * utilizationFactor,
            baseRequirements.getMemory() * loadFactor * utilizationFactor,
            baseRequirements.getBandwidth() * loadFactor
        );
        
        return dynamicReqs;
    }
    
    /**
     * Implementation of f(λ(t), µi) from the paper (Equation 17)
     * Captures workload dynamics impact on resource requirements
     */
    private double calculateWorkloadDynamics(double requestRate) {
        // η is workload sensitivity parameter, set to 0.5
        double eta = 0.5;
        return 1 + eta * (requestRate / serviceRate - 1);
    }
    
    /**
     * Implementation of g(U(Si, t)) from the paper (Equation 18)
     * Reflects resource utilization impact
     */
    private double calculateUtilizationImpact() {
        // Use CPU utilization for calculation
        double cpuUtilization = resourceUtilization.get("cpu");
        double utilizationThreshold = 0.8;  // Uth in the paper
        
        if (cpuUtilization <= utilizationThreshold) {
            return 1.0;
        } else {
            // Beta controls exponential growth rate (β = 2 in the paper)
            double beta = 2.0;
            return Math.exp(beta * (cpuUtilization - utilizationThreshold));
        }
    }
    
    /**
     * Updates the current resource utilization
     */
    public void updateResourceUtilization(String resource, double value) {
        resourceUtilization.put(resource, value);
    }
    
    // Getters and setters
    public String getId() { return id; }
    
    public String getNodeId() { return nodeId; }
    
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    
    public ResourceRequirements getBaseRequirements() { return baseRequirements; }
    
    public double getExecutionTime() { return executionTime; }
    
    public double getServiceRate() { return serviceRate; }
    
    public void setServiceRate(double serviceRate) { this.serviceRate = serviceRate; }
    
    public Map<String, Double> getResourceUtilization() { return resourceUtilization; }
}

/**
 * ResourceRequirements.java
 * 
 * This class represents the resource requirements of a microservice,
 * including CPU, memory, and bandwidth needs.
 */
package com.sdtmcs.model;

public class ResourceRequirements {
    private double cpu;      // CPU cores
    private double memory;   // Memory in MB
    private double bandwidth; // Network bandwidth in Mbps
    
    public ResourceRequirements(double cpu, double memory, double bandwidth) {
        this.cpu = cpu;
        this.memory = memory;
        this.bandwidth = bandwidth;
    }
    
    // Getters
    public double getCpu() { return cpu; }
    public double getMemory() { return memory; }
    public double getBandwidth() { return bandwidth; }
    
    /**
     * Checks if these requirements can be satisfied by the given capacity
     */
    public boolean canFit(ResourceCapacity capacity) {
        return cpu <= capacity.getAvailableCpu() &&
               memory <= capacity.getAvailableMemory() &&
               bandwidth <= capacity.getAvailableBandwidth();
    }
}

/**
 * ResourceCapacity.java
 * 
 * This class represents the resource capacity of a node in the cloud-edge environment,
 * tracking both total and available resources.
 */
package com.sdtmcs.model;

public class ResourceCapacity {
    private double totalCpu;
    private double totalMemory;
    private double totalBandwidth;
    
    private double availableCpu;
    private double availableMemory;
    private double availableBandwidth;
    
    public ResourceCapacity(double totalCpu, double totalMemory, double totalBandwidth) {
        this.totalCpu = totalCpu;
        this.totalMemory = totalMemory;
        this.totalBandwidth = totalBandwidth;
        
        // Initially all resources are available
        this.availableCpu = totalCpu;
        this.availableMemory = totalMemory;
        this.availableBandwidth = totalBandwidth;
    }
    
    /**
     * Allocates resources for a service
     * 
     * @return true if allocation was successful, false if insufficient resources
     */
    public boolean allocate(ResourceRequirements requirements) {
        if (!requirements.canFit(this)) {
            return false;
        }
        
        availableCpu -= requirements.getCpu();
        availableMemory -= requirements.getMemory();
        availableBandwidth -= requirements.getBandwidth();
        
        return true;
    }
    
    /**
     * Releases resources previously allocated to a service
     */
    public void release(ResourceRequirements requirements) {
        availableCpu = Math.min(totalCpu, availableCpu + requirements.getCpu());
        availableMemory = Math.min(totalMemory, availableMemory + requirements.getMemory());
        availableBandwidth = Math.min(totalBandwidth, availableBandwidth + requirements.getBandwidth());
    }
    
    // Getters
    public double getTotalCpu() { return totalCpu; }
    public double getTotalMemory() { return totalMemory; }
    public double getTotalBandwidth() { return totalBandwidth; }
    
    public double getAvailableCpu() { return availableCpu; }
    public double getAvailableMemory() { return availableMemory; }
    public double getAvailableBandwidth() { return availableBandwidth; }
    
    /**
     * Gets current CPU utilization ratio
     */
    public double getCpuUtilization() {
        return (totalCpu - availableCpu) / totalCpu;
    }
    
    /**
     * Gets current memory utilization ratio
     */
    public double getMemoryUtilization() {
        return (totalMemory - availableMemory) / totalMemory;
    }
    
    /**
     * Gets current bandwidth utilization ratio
     */
    public double getBandwidthUtilization() {
        return (totalBandwidth - availableBandwidth) / totalBandwidth;
    }
}