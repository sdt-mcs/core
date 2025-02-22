/**
 * CriticalPathAnalyzer.java
 * 
 * This class implements the multi-dimensional critical path analysis algorithm
 * described in the paper, which considers temporal criticality, resource criticality,
 * and dependency criticality simultaneously.
 */
package com.sdtmcs.analysis;

import com.sdtmcs.model.*;
import java.util.*;

public class CriticalPathAnalyzer {
    // Default weighting factors for criticality dimensions
    private double alphaWeight = 0.4;  // Temporal criticality weight
    private double betaWeight = 0.35;  // Resource criticality weight
    private double gammaWeight = 0.25; // Dependency criticality weight
    
    // Critical path threshold
    private double criticalThreshold = 0.7;
    
    private ServiceDependencyGraph dependencyGraph;
    private Map<String, EdgeNode> nodes;
    
    public CriticalPathAnalyzer(ServiceDependencyGraph dependencyGraph, Map<String, EdgeNode> nodes) {
        this.dependencyGraph = dependencyGraph;
        this.nodes = nodes;
    }
    
    /**
     * Identifies critical paths in the service dependency graph
     * 
     * @return List of critical paths (each path is a list of service IDs)
     */
    public List<List<String>> identifyCriticalPaths(String sourceId, String targetId) {
        List<List<String>> allPaths = dependencyGraph.getAllPaths(sourceId, targetId);
        List<List<String>> criticalPaths = new ArrayList<>();
        
        // Calculate criticality scores for all paths
        Map<List<String>, Double> pathScores = new HashMap<>();
        double maxTemporal = Double.MIN_VALUE;
        double minTemporal = Double.MAX_VALUE;
        double maxResource = Double.MIN_VALUE;
        double minResource = Double.MAX_VALUE;
        double maxDependency = Double.MIN_VALUE;
        double minDependency = Double.MAX_VALUE;
        
        // First pass: calculate raw criticality measures and find min/max values
        Map<List<String>, Double> temporalCriticality = new HashMap<>();
        Map<List<String>, Double> resourceCriticality = new HashMap<>();
        Map<List<String>, Double> dependencyCriticality = new HashMap<>();
        
        for (List<String> path : allPaths) {
            // Calculate temporal criticality
            double temporal = calculateTemporalCriticality(path);
            temporalCriticality.put(path, temporal);
            maxTemporal = Math.max(maxTemporal, temporal);
            minTemporal = Math.min(minTemporal, temporal);
            
            // Calculate resource criticality
            double resource = calculateResourceCriticality(path);
            resourceCriticality.put(path, resource);
            maxResource = Math.max(maxResource, resource);
            minResource = Math.min(minResource, resource);
            
            // Calculate dependency criticality
            double dependency = calculateDependencyCriticality(path);
            dependencyCriticality.put(path, dependency);
            maxDependency = Math.max(maxDependency, dependency);
            minDependency = Math.min(minDependency, dependency);
        }
        
        // Second pass: normalize and combine criticality measures
        for (List<String> path : allPaths) {
            // Normalize each dimension to [0,1] range as per Equations 8-10
            double normTemporal = normalizeValue(temporalCriticality.get(path), minTemporal, maxTemporal);
            double normResource = normalizeValue(resourceCriticality.get(path), minResource, maxResource);
            double normDependency = normalizeValue(dependencyCriticality.get(path), minDependency, maxDependency);
            
            // Calculate overall criticality score using Equation 7
            double criticality = alphaWeight * normTemporal + 
                                betaWeight * normResource + 
                                gammaWeight * normDependency;
            
            pathScores.put(path, criticality);
            
            // Check if path exceeds critical threshold (Equation 12)
            if (criticality > criticalThreshold) {
                criticalPaths.add(path);
            }
        }
        
        // Sort critical paths by criticality score (highest first)
        criticalPaths.sort((p1, p2) -> Double.compare(pathScores.get(p2), pathScores.get(p1)));
        
        return criticalPaths;
    }
    
    /**
     * Calculates temporal criticality based on end-to-end latency
     * Implementation of Equation 13 from the paper
     */
    private double calculateTemporalCriticality(List<String> path) {
        // For simplicity, we use the sequential latency calculation
        return dependencyGraph.calculateSequentialLatency(path);
    }
    
    /**
     * Calculates resource criticality based on resource pressure
     * Implementation of Equations 15-16 from the paper
     */
    private double calculateResourceCriticality(List<String> path) {
        double resourceCriticality = 0.0;
        
        for (String serviceId : path) {
            Microservice service = getServiceById(serviceId);
            if (service == null || service.getNodeId() == null) continue;
            
            EdgeNode node = nodes.get(service.getNodeId());
            if (node == null) continue;
            
            // R(Si, t) / Cap(Node(Si)) as in Equation 15
            ResourceRequirements baseReqs = service.getBaseRequirements();
            ResourceCapacity nodeCap = node.getCapacity();
            
            double cpuPressure = baseReqs.getCpu() / nodeCap.getTotalCpu();
            double memPressure = baseReqs.getMemory() / nodeCap.getTotalMemory();
            double bwPressure = baseReqs.getBandwidth() / nodeCap.getTotalBandwidth();
            
            // Weighted sum of resource pressures
            double resourcePressure = 0.5 * cpuPressure + 0.3 * memPressure + 0.2 * bwPressure;
            
            // Multiply by current utilization as in Equation 15
            double utilization = node.getCapacity().getCpuUtilization();
            
            resourceCriticality += resourcePressure * utilization;
        }
        
        return resourceCriticality;
    }
    
    /**
     * Calculates dependency criticality based on service dependencies
     * Implementation of Equations 21-22 from the paper
     */
    private double calculateDependencyCriticality(List<String> path) {
        double dependencyCriticality = 0.0;
        
        for (String serviceId : path) {
            // |dep(Si)| - number of dependent services
            List<ServiceDependencyGraph.Edge> dependencies = dependencyGraph.getDependencies(serviceId);
            if (dependencies.isEmpty()) continue;
            
            int depCount = dependencies.size();
            
            // Calculate dependency impact factor I(Si) per Equation 22
            double totalImpact = 0.0;
            for (ServiceDependencyGraph.Edge edge : dependencies) {
                // freq(Si, Sj) * data(Si, Sj)
                double impact = edge.getFrequency() * edge.getDataVolume();
                totalImpact += impact;
            }
            
            double impactFactor = totalImpact / depCount;
            
            // D(p) calculation from Equation 21
            dependencyCriticality += depCount * impactFactor;
        }
        
        return dependencyCriticality;
    }
    
    /**
     * Normalizes a value to [0,1] range
     */
    private double normalizeValue(double value, double min, double max) {
        if (max == min) return 0.5; // Avoid division by zero
        return (value - min) / (max - min);
    }
    
    /**
     * Helper method to get service by ID
     */
    private Microservice getServiceById(String serviceId) {
        for (Microservice service : dependencyGraph.getAllServices()) {
            if (service.getId().equals(serviceId)) {
                return service;
            }
        }
        return null;
    }
    
    /**
     * Updates criticality weights using z-score normalization
     * Implementation of Equation 11 from the paper
     */
    public void updateWeights(double alpha, double beta, double gamma) {
        // Calculate mean and standard deviation
        double[] weights = {alpha, beta, gamma};
        double mean = (alpha + beta + gamma) / 3.0;
        
        double sumSquaredDiff = 0.0;
        for (double w : weights) {
            sumSquaredDiff += Math.pow(w - mean, 2);
        }
        double stdDev = Math.sqrt(sumSquaredDiff / 3.0);
        
        // Apply z-score normalization if standard deviation is non-zero
        if (stdDev > 0.001) {
            alphaWeight = (alpha - mean) / stdDev;
            betaWeight = (beta - mean) / stdDev;
            gammaWeight = (gamma - mean) / stdDev;
            
            // Normalize to ensure sum = 1
            double sum = alphaWeight + betaWeight + gammaWeight;
            alphaWeight /= sum;
            betaWeight /= sum;
            gammaWeight /= sum;
        } else {
            // Default weights if no significant variation
            alphaWeight = 0.4;
            betaWeight = 0.35;
            gammaWeight = 0.25;
        }
    }
    
    /**
     * Updates the critical path threshold
     */
    public void setCriticalThreshold(double threshold) {
        this.criticalThreshold = threshold;
    }
    
    // Getters for weights
    public double getAlphaWeight() { return alphaWeight; }
    public double getBetaWeight() { return betaWeight; }
    public double getGammaWeight() { return gammaWeight; }
    public double getCriticalThreshold() { return criticalThreshold; }
}

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
    
    // Getters
    public String getId() { return id; }
    public boolean isEdgeNode() { return isEdgeNode; }
    public ResourceCapacity getCapacity() { return capacity; }
    public List<Microservice> getDeployedServices() { return deployedServices; }
}