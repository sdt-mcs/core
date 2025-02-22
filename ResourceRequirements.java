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
    
    /**
     * Scales resource requirements by a factor
     */
    public ResourceRequirements scale(double factor) {
        return new ResourceRequirements(
            cpu * factor,
            memory * factor,
            bandwidth * factor
        );
    }
    
    /**
     * Creates a new resource requirements object by adding another's requirements
     */
    public ResourceRequirements add(ResourceRequirements other) {
        return new ResourceRequirements(
            this.cpu + other.cpu,
            this.memory + other.memory,
            this.bandwidth + other.bandwidth
        );
    }
    
    /**
     * Creates a new resource requirements object by subtracting another's requirements
     */
    public ResourceRequirements subtract(ResourceRequirements other) {
        return new ResourceRequirements(
            Math.max(0, this.cpu - other.cpu),
            Math.max(0, this.memory - other.memory),
            Math.max(0, this.bandwidth - other.bandwidth)
        );
    }
    
    /**
     * Returns resource requirements as a string
     */
    @Override
    public String toString() {
        return String.format("CPU: %.2f cores, Memory: %.2f MB, Bandwidth: %.2f Mbps", 
                           cpu, memory, bandwidth);
    }
    
    /**
     * Creates resource requirements based on service type
     */
    public static ResourceRequirements forServiceType(String type) {
        switch (type) {
            case "computation-intensive":
                return new ResourceRequirements(0.8, 800, 15);
            case "io-intensive":
                return new ResourceRequirements(0.3, 1500, 40);
            case "hybrid":
                return new ResourceRequirements(0.5, 1000, 30);
            default:
                return new ResourceRequirements(0.3, 500, 10); // Default light requirements
        }
    }
}