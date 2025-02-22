/**
 * TopologyAwareDeployment.java
 * 
 * This class implements the topology-aware service deployment algorithm
 * described in Algorithm 1 of the paper. It uses dependency analysis and
 * critical path information to optimize microservice placement.
 */
package com.sdtmcs.deployment;

import com.sdtmcs.analysis.CriticalPathAnalyzer;
import com.sdtmcs.model.*;
import java.util.*;

public class TopologyAwareDeployment {
    private ServiceDependencyGraph dependencyGraph;
    private Map<String, EdgeNode> nodes;
    private CriticalPathAnalyzer pathAnalyzer;
    private Map<String, List<String>> criticalPaths;
    private double criticalPathThreshold = 0.7;
    
    // Federated learning parameters
    private Map<String, Double> globalParameters;
    private int maxIterations = 10;
    private double convergenceThreshold = 0.01;
    
    public TopologyAwareDeployment(ServiceDependencyGraph dependencyGraph, Map<String, EdgeNode> nodes) {
        this.dependencyGraph = dependencyGraph;
        this.nodes = nodes;
        this.pathAnalyzer = new CriticalPathAnalyzer(dependencyGraph, nodes);
        this.criticalPaths = new HashMap<>();
        this.globalParameters = new HashMap<>();
        
        // Initialize default global parameters
        initializeGlobalParameters();
    }
    
    /**
     * Initializes default global parameters for optimization
     */
    private void initializeGlobalParameters() {
        globalParameters.put("temporalWeight", 0.4);
        globalParameters.put("resourceWeight", 0.35);
        globalParameters.put("dependencyWeight", 0.25);
        globalParameters.put("criticalPathThreshold", 0.7);
        globalParameters.put("utilizationThreshold", 0.8);
        globalParameters.put("resourceScalingFactor", 0.3);
    }
    
    /**
     * Executes the topology-aware service deployment algorithm
     * Implementation of Algorithm 1 from the paper
     * 
     * @return A mapping of service IDs to node IDs
     */
    public Map<String, String> executeDeployment() {
        Map<String, String> placement = new HashMap<>();
        
        // Phase 1: Critical path identification (lines 1-5 in Algorithm 1)
        identifyCriticalPaths();
        
        // Phase 2: Topology-aware service pre-deployment (lines 6-13)
        for (List<String> path : getAllCriticalPaths()) {
            for (String serviceId : path) {
                // Skip if already placed
                if (placement.containsKey(serviceId)) {
                    continue;
                }
                
                // Filter suitable nodes for this service
                List<EdgeNode> suitableNodes = filterNodes(serviceId);
                
                // Find best node based on placement cost
                EdgeNode bestNode = findBestNode(serviceId, suitableNodes, placement);
                if (bestNode != null) {
                    placement.put(serviceId, bestNode.getId());
                    
                    // Get the service object and update its node ID
                    for (Microservice service : dependencyGraph.getAllServices()) {
                        if (service.getId().equals(serviceId)) {
                            bestNode.deployService(service);
                            break;
                        }
                    }
                }
            }
        }
        
        // Phase 3: Place remaining non-critical services
        for (Microservice service : dependencyGraph.getAllServices()) {
            String serviceId = service.getId();
            if (!placement.containsKey(serviceId)) {
                List<EdgeNode> suitableNodes = filterNodes(serviceId);
                EdgeNode bestNode = findBestNode(serviceId, suitableNodes, placement);
                
                if (bestNode != null) {
                    placement.put(serviceId, bestNode.getId());
                    bestNode.deployService(service);
                }
            }
        }
        
        // Phase 4: Federated refinement (lines 14-18)
        boolean converged = false;
        int iteration = 0;
        
        while (!converged && iteration < maxIterations) {
            // Update local models from each node
            Map<String, Map<String, Double>> localParams = collectLocalParameters();
            
            // Aggregate to global model
            Map<String, Double> newGlobalParams = federatedAggregation(localParams);
            
            // Check convergence
            double paramChange = calculateParameterChange(newGlobalParams);
            converged = paramChange < convergenceThreshold;
            
            // Update global model
            globalParameters = newGlobalParams;
            
            // Refine placement strategy
            placement = refineStrategy(placement);
            
            iteration++;
        }
        
        return placement;
    }
    
    /**
     * Identifies critical paths in service chains
     * Corresponds to lines 1-5 in Algorithm 1
     */
    private void identifyCriticalPaths() {
        criticalPaths.clear();
        
        // Update path analyzer with current weights from global parameters
        pathAnalyzer.updateWeights(
            globalParameters.get("temporalWeight"),
            globalParameters.get("resourceWeight"),
            globalParameters.get("dependencyWeight")
        );
        
        // Set critical threshold from global parameters
        pathAnalyzer.setCriticalThreshold(globalParameters.get("criticalPathThreshold"));
        
        // For each service chain, identify critical paths
        for (Microservice service : dependencyGraph.getAllServices()) {
            // Find chain endpoints (sources and sinks)
            Set<String> sources = findChainSources();
            Set<String> sinks = findChainSinks();
            
            // For each source-sink pair, identify critical paths
            for (String source : sources) {
                for (String sink : sinks) {
                    String chainId = source + "-" + sink;
                    List<List<String>> paths = pathAnalyzer.identifyCriticalPaths(source, sink);
                    
                    if (!paths.isEmpty()) {
                        criticalPaths.put(chainId, paths.get(0)); // Store most critical path
                    }
                }
            }
        }
    }
    
    /**
     * Finds source services (those with no incoming dependencies)
     */
    private Set<String> findChainSources() {
        Set<String> sources = new HashSet<>();
        Set<String> allServices = new HashSet<>();
        Set<String> servicesWithIncoming = new HashSet<>();
        
        // Collect all services and those with incoming edges
        for (Microservice service : dependencyGraph.getAllServices()) {
            String serviceId = service.getId();
            allServices.add(serviceId);
            
            for (ServiceDependencyGraph.Edge edge : dependencyGraph.getDependencies(serviceId)) {
                servicesWithIncoming.add(edge.getTargetId());
            }
        }
        
        // Services with no incoming edges are sources
        for (String serviceId : allServices) {
            if (!servicesWithIncoming.contains(serviceId)) {
                sources.add(serviceId);
            }
        }
        
        return sources;
    }
    
    /**
     * Finds sink services (those with no outgoing dependencies)
     */
    private Set<String> findChainSinks() {
        Set<String> sinks = new HashSet<>();
        
        // Collect all services
        for (Microservice service : dependencyGraph.getAllServices()) {
            String serviceId = service.getId();
            
            // If service has no dependencies, it's a sink
            if (dependencyGraph.getDependencies(serviceId).isEmpty()) {
                sinks.add(serviceId);
            }
        }
        
        return sinks;
    }
    
    /**
     * Returns all critical paths identified
     */
    private List<List<String>> getAllCriticalPaths() {
        List<List<String>> allPaths = new ArrayList<>();
        
        for (List<String> path : criticalPaths.values()) {
            allPaths.add(path);
        }
        
        return allPaths;
    }
    
    /**
     * Filters suitable nodes for deploying a service
     * Implementation of Algorithm 1, line 8
     */
    private List<EdgeNode> filterNodes(String serviceId) {
        List<EdgeNode> suitableNodes = new ArrayList<>();
        
        // Get the service object
        Microservice service = null;
        for (Microservice s : dependencyGraph.getAllServices()) {
            if (s.getId().equals(serviceId)) {
                service = s;
                break;
            }
        }
        
        if (service == null) {
            return suitableNodes;
        }
        
        // Filter nodes based on resource requirements
        ResourceRequirements requirements = service.getBaseRequirements();
        
        for (EdgeNode node : nodes.values()) {
            if (requirements.canFit(node.getCapacity())) {
                suitableNodes.add(node);
            }
        }
        
        return suitableNodes;
    }
    
    /**
     * Finds the best node for deploying a service
     * Implementation of Algorithm 1, line 9
     */
    private EdgeNode findBestNode(String serviceId, List<EdgeNode> suitableNodes, 
                                 Map<String, String> currentPlacement) {
        if (suitableNodes.isEmpty()) {
            return null;
        }
        
        // Get the service object
        Microservice service = null;
        for (Microservice s : dependencyGraph.getAllServices()) {
            if (s.getId().equals(serviceId)) {
                service = s;
                break;
            }
        }
        
        if (service == null) {
            return null;
        }
        
        EdgeNode bestNode = null;
        double lowestCost = Double.MAX_VALUE;
        
        for (EdgeNode node : suitableNodes) {
            double cost = calculatePlacementCost(service, node, currentPlacement);
            
            if (cost < lowestCost) {
                lowestCost = cost;
                bestNode = node;
            }
        }
        
        return bestNode;
    }
    
    /**
     * Calculates the cost of placing a service on a node
     */
    private double calculatePlacementCost(Microservice service, EdgeNode node, 
                                        Map<String, String> currentPlacement) {
        double communicationCost = 0.0;
        double resourceCost = 0.0;
        double loadBalanceCost = 0.0;
        
        // Calculate communication cost with dependent services
        String serviceId = service.getId();
        List<ServiceDependencyGraph.Edge> dependencies = dependencyGraph.getDependencies(serviceId);
        
        for (ServiceDependencyGraph.Edge edge : dependencies) {
            String targetId = edge.getTargetId();
            
            // If dependent service is already placed, consider communication cost
            if (currentPlacement.containsKey(targetId)) {
                String targetNodeId = currentPlacement.get(targetId);
                
                // If on different nodes, add communication cost
                if (!node.getId().equals(targetNodeId)) {
                    EdgeNode targetNode = nodes.get(targetNodeId);
                    double delay = node.getNetworkDelay(targetNodeId);
                    double dataVolume = edge.getDataVolume();
                    
                    communicationCost += delay * dataVolume * edge.getFrequency();
                }
            }
        }
        
        // Calculate resource cost
        ResourceRequirements reqs = service.getBaseRequirements();
        ResourceCapacity capacity = node.getCapacity();
        
        resourceCost = (reqs.getCpu() / capacity.getTotalCpu()) * 
                     (reqs.getMemory() / capacity.getTotalMemory()) * 
                     (reqs.getBandwidth() / capacity.getTotalBandwidth());
        
        // Calculate load balance cost
        loadBalanceCost = capacity.getCpuUtilization() + 
                        capacity.getMemoryUtilization() + 
                        capacity.getBandwidthUtilization();
        
        // Weighted sum of costs
        return 0.5 * communicationCost + 0.3 * resourceCost + 0.2 * loadBalanceCost;
    }
    
    /**
     * Collects local parameters from all nodes
     */
    private Map<String, Map<String, Double>> collectLocalParameters() {
        Map<String, Map<String, Double>> localParams = new HashMap<>();
        
        // In practice, this would collect parameters from node agents
        // Simplified implementation for demonstration
        for (EdgeNode node : nodes.values()) {
            Map<String, Double> nodeParams = new HashMap<>(globalParameters);
            
            // Add some local variation
            nodeParams.put("utilizationThreshold", 
                         globalParameters.get("utilizationThreshold") * 
                         (0.9 + Math.random() * 0.2)); // ±10% variation
            
            localParams.put(node.getId(), nodeParams);
        }
        
        return localParams;
    }
    
    /**
     * Performs federated aggregation of local parameters
     * Implementation of Algorithm 1, line 16
     */
    private Map<String, Double> federatedAggregation(Map<String, Map<String, Double>> localParams) {
        Map<String, Double> aggregatedParams = new HashMap<>();
        
        // For each parameter, compute weighted average
        for (String paramName : globalParameters.keySet()) {
            double sum = 0.0;
            double weightSum = 0.0;
            
            for (String nodeId : localParams.keySet()) {
                EdgeNode node = nodes.get(nodeId);
                double nodeWeight = calculateNodeWeight(node);
                
                Map<String, Double> nodeParams = localParams.get(nodeId);
                if (nodeParams.containsKey(paramName)) {
                    sum += nodeParams.get(paramName) * nodeWeight;
                    weightSum += nodeWeight;
                }
            }
            
            if (weightSum > 0) {
                aggregatedParams.put(paramName, sum / weightSum);
            } else {
                // Fallback to current global value
                aggregatedParams.put(paramName, globalParameters.get(paramName));
            }
        }
        
        return aggregatedParams;
    }
    
    /**
     * Calculates weighting factor for a node in federated aggregation
     */
    private double calculateNodeWeight(EdgeNode node) {
        // Simple weighting based on node capacity
        ResourceCapacity capacity = node.getCapacity();
        
        return capacity.getTotalCpu() * 0.5 + 
               capacity.getTotalMemory() / 1000.0 * 0.3 + 
               capacity.getTotalBandwidth() / 100.0 * 0.2;
    }
    
    /**
     * Calculates the magnitude of parameter changes
     */
    private double calculateParameterChange(Map<String, Double> newParams) {
        double sumSquaredDiff = 0.0;
        
        for (String paramName : globalParameters.keySet()) {
            double oldValue = globalParameters.get(paramName);
            double newValue = newParams.getOrDefault(paramName, oldValue);
            
            sumSquaredDiff += Math.pow(newValue - oldValue, 2);
        }
        
        return Math.sqrt(sumSquaredDiff);
    }
    
    /**
     * Refines the placement strategy based on updated global parameters
     * Implementation of Algorithm 1, line 17
     */
    private Map<String, String> refineStrategy(Map<String, String> currentPlacement) {
        // Update critical path analyzer with new parameters
        pathAnalyzer.updateWeights(
            globalParameters.get("temporalWeight"),
            globalParameters.get("resourceWeight"),
            globalParameters.get("dependencyWeight")
        );
        
        pathAnalyzer.setCriticalThreshold(globalParameters.get("criticalPathThreshold"));
        
        // Re-identify critical paths
        identifyCriticalPaths();
        
        // For each critical path, check if placement can be improved
        Map<String, String> refinedPlacement = new HashMap<>(currentPlacement);
        Set<String> processedServices = new HashSet<>();
        
        for (List<String> path : getAllCriticalPaths()) {
            for (String serviceId : path) {
                // Skip if already processed in this refinement round
                if (processedServices.contains(serviceId)) {
                    continue;
                }
                
                processedServices.add(serviceId);
                
                // Current node
                String currentNodeId = refinedPlacement.get(serviceId);
                if (currentNodeId == null) continue;
                
                // Get the service object
                Microservice service = null;
                for (Microservice s : dependencyGraph.getAllServices()) {
                    if (s.getId().equals(serviceId)) {
                        service = s;
                        break;
                    }
                }
                
                if (service == null) continue;
                
                // Check if better placement exists
                List<EdgeNode> suitableNodes = filterNodes(serviceId);
                double currentCost = calculatePlacementCost(
                    service, nodes.get(currentNodeId), refinedPlacement);
                
                for (EdgeNode node : suitableNodes) {
                    if (node.getId().equals(currentNodeId)) continue;
                    
                    double newCost = calculatePlacementCost(
                        service, node, refinedPlacement);
                    
                    // If significantly better placement found, migrate
                    if (newCost < currentCost * 0.8) { // 20% improvement threshold
                        // Remove from current node
                        EdgeNode currentNode = nodes.get(currentNodeId);
                        currentNode.removeService(service);
                        
                        // Deploy to new node
                        node.deployService(service);
                        refinedPlacement.put(serviceId, node.getId());
                        break;
                    }
                }
            }
        }
        
        return refinedPlacement;
    }
    
    /**
     * Gets the current global parameters
     */
    public Map<String, Double> getGlobalParameters() {
        return new HashMap<>(globalParameters);
    }
    
    /**
     * Sets global parameters for optimization
     */
    public void setGlobalParameters(Map<String, Double> parameters) {
        this.globalParameters.putAll(parameters);
    }
}