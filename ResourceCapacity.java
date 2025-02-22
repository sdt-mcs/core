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
    
    /**
     * Updates resource capacity with new total values
     */
    public void updateCapacity(double newTotalCpu, double newTotalMemory, double newTotalBandwidth) {
        // Calculate the change in capacity
        double cpuChange = newTotalCpu - totalCpu;
        double memoryChange = newTotalMemory - totalMemory;
        double bandwidthChange = newTotalBandwidth - totalBandwidth;
        
        // Update total capacity
        totalCpu = newTotalCpu;
        totalMemory = newTotalMemory;
        totalBandwidth = newTotalBandwidth;
        
        // Update available capacity
        availableCpu = Math.min(totalCpu, availableCpu + cpuChange);
        availableMemory = Math.min(totalMemory, availableMemory + memoryChange);
        availableBandwidth = Math.min(totalBandwidth, availableBandwidth + bandwidthChange);
    }
    
    /**
     * Creates a copy of the resource capacity
     */
    public ResourceCapacity copy() {
        ResourceCapacity copy = new ResourceCapacity(totalCpu, totalMemory, totalBandwidth);
        copy.availableCpu = availableCpu;
        copy.availableMemory = availableMemory;
        copy.availableBandwidth = availableBandwidth;
        return copy;
    }
    
    // Getters
    public double getTotalCpu() { return totalCpu; }
    public double getTotalMemory() { return totalMemory; }
    public double getTotalBandwidth() { return totalBandwidth; }
    
    public double getAvailableCpu() { return availableCpu; }
    public double getAvailableMemory() { return availableMemory; }
    public double getAvailableBandwidth() { return availableBandwidth; }
    
    public double getUsedCpu() { return totalCpu - availableCpu; }
    public double getUsedMemory() { return totalMemory - availableMemory; }
    public double getUsedBandwidth() { return totalBandwidth - availableBandwidth; }
    
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
    
    /**
     * Gets average utilization across all resource types
     */
    public double getAverageUtilization() {
        return (getCpuUtilization() + getMemoryUtilization() + getBandwidthUtilization()) / 3.0;
    }
    
    /**
     * Determines if the capacity is overloaded
     */
    public boolean isOverloaded(double threshold) {
        return getCpuUtilization() > threshold || 
               getMemoryUtilization() > threshold || 
               getBandwidthUtilization() > threshold;
    }
    
    /**
     * Creates a node capacity for edge node
     */
    public static ResourceCapacity createEdgeCapacity() {
        return new ResourceCapacity(4.0, 8000, 100);
    }
    
    /**
     * Creates a node capacity for cloud node
     */
    public static ResourceCapacity createCloudCapacity() {
        return new ResourceCapacity(16.0, 32000, 1000);
    }
    
    /**
     * Returns resource capacity as a string
     */
    @Override
    public String toString() {
        return String.format("Total [CPU: %.2f cores, Memory: %.2f MB, Bandwidth: %.2f Mbps], " +
                           "Available [CPU: %.2f cores, Memory: %.2f MB, Bandwidth: %.2f Mbps]", 
                           totalCpu, totalMemory, totalBandwidth,
                           availableCpu, availableMemory, availableBandwidth);
    }
}