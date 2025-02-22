/**
 * FederatedLearningEngine.java
 * 
 * This class implements the federated learning mechanism for topology optimization
 * as described in Section 3.2 of the paper. It manages model updates, parameter
 * aggregation, and global optimization of service placement.
 */
package com.sdtmcs.learning;

import com.sdtmcs.model.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FederatedLearningEngine {
    // Learning parameters
    private double baseLearningRate = 0.001;
    private double adaptivityFactor = 0.5;
    private int roundDuration = 300; // 5 minutes in seconds
    
    // Node state tracking
    private Map<String, NodeState> nodeStates;
    private Map<String, NodeModel> nodeModels;
    private GlobalModel globalModel;
    
    // Selection thresholds for aggregation
    private int quantityThreshold = 3;
    private double qualityThreshold = 0.7;
    
    public FederatedLearningEngine() {
        this.nodeStates = new ConcurrentHashMap<>();
        this.nodeModels = new ConcurrentHashMap<>();
        this.globalModel = new GlobalModel();
    }
    
    /**
     * Registers a node with the federated learning system
     */
    public void registerNode(String nodeId, EdgeNode node) {
        nodeStates.put(nodeId, new NodeState(nodeId));
        nodeModels.put(nodeId, new NodeModel(nodeId));
    }
    
    /**
     * Updates node state with latest monitoring information
     * Implementation of Equation 23-27 from the paper
     */
    public void updateNodeState(String nodeId, Map<String, Double> resourceState, 
                               Map<String, List<Double>> pathCriticality, 
                               Map<String, Double> latencyStats) {
        NodeState state = nodeStates.get(nodeId);
        if (state == null) return;
        
        // Update resource state (Equation 24)
        state.setResourceState(resourceState);
        
        // Update path criticality (Equation 25)
        state.setPathCriticality(pathCriticality);
        
        // Update latency statistics (Equation 27)
        state.setLatencyStats(latencyStats);
    }
    
    /**
     * Executes one round of federated learning
     * Updates local models and aggregates to global model
     */
    public void executeRound() {
        // 1. Local update phase
        for (String nodeId : nodeModels.keySet()) {
            NodeModel model = nodeModels.get(nodeId);
            NodeState state = nodeStates.get(nodeId);
            
            if (state != null) {
                // Perform local update based on node state
                model.updateParameters(state, calculateLearningRate(state));
            }
        }
        
        // 2. Global aggregation phase
        aggregateModels();
        
        // 3. Distribute updated global model to nodes
        distributeGlobalModel();
    }
    
    /**
     * Calculates adaptive learning rate based on chain latency variance
     * Implementation of Equation 32 from the paper
     */
    private double calculateLearningRate(NodeState state) {
        double chainVariance = state.getLatencyStats().getOrDefault("chainVariance", 1.0);
        return Math.min(baseLearningRate, 
                       baseLearningRate / Math.sqrt(1 + adaptivityFactor * chainVariance));
    }
    
    /**
     * Aggregates local models into global model using weighted average
     * Implementation of Equation 33-34 from the paper
     */
    private void aggregateModels() {
        Set<String> selectedNodes = selectNodesForAggregation();
        
        if (selectedNodes.isEmpty()) {
            return; // No nodes meet the selection criteria
        }
        
        Map<String, Double> nodeWeights = calculateNodeWeights(selectedNodes);
        Map<String, Double> parameterSums = new HashMap<>();
        Map<String, Double> weightSums = new HashMap<>();
        
        // Calculate weighted parameter sums
        for (String nodeId : selectedNodes) {
            NodeModel model = nodeModels.get(nodeId);
            double weight = nodeWeights.get(nodeId) * Math.sqrt(model.getSampleCount());
            
            for (Map.Entry<String, Double> param : model.getParameters().entrySet()) {
                String paramName = param.getKey();
                double weightedValue = param.getValue() * weight;
                
                parameterSums.put(paramName, 
                                 parameterSums.getOrDefault(paramName, 0.0) + weightedValue);
                weightSums.put(paramName, 
                              weightSums.getOrDefault(paramName, 0.0) + weight);
            }
        }
        
        // Update global model parameters
        for (String paramName : parameterSums.keySet()) {
            double weightSum = weightSums.getOrDefault(paramName, 1.0);
            if (weightSum > 0) {
                double aggregatedValue = parameterSums.get(paramName) / weightSum;
                globalModel.setParameter(paramName, aggregatedValue);
            }
        }
    }
    
    /**
     * Selects nodes for model aggregation based on quantity and quality thresholds
     * Implementation of Equation 34 from the paper
     */
    private Set<String> selectNodesForAggregation() {
        Set<String> selectedNodes = new HashSet<>();
        
        for (String nodeId : nodeModels.keySet()) {
            NodeModel model = nodeModels.get(nodeId);
            NodeState state = nodeStates.get(nodeId);
            
            if (model.getSampleCount() >= quantityThreshold && 
                model.getQualityScore() >= qualityThreshold) {
                selectedNodes.add(nodeId);
            }
        }
        
        return selectedNodes;
    }
    
    /**
     * Calculates node weights for aggregation based on capability and stability
     * Implementation of Equation 70 from the paper (adapted for this context)
     */
    private Map<String, Double> calculateNodeWeights(Set<String> selectedNodes) {
        Map<String, Double> weights = new HashMap<>();
        double totalWeight = 0.0;
        
        for (String nodeId : selectedNodes) {
            EdgeNode node = getNodeById(nodeId);
            double failRate = nodeStates.get(nodeId).getFailRate();
            
            // Calculate capability-weighted stability score
            double nodeCapability = calculateNodeCapability(node);
            double stabilityFactor = 1.0 - failRate;
            double nodeWeight = nodeCapability * stabilityFactor;
            
            weights.put(nodeId, nodeWeight);
            totalWeight += nodeWeight;
        }
        
        // Normalize weights
        if (totalWeight > 0) {
            for (String nodeId : weights.keySet()) {
                weights.put(nodeId, weights.get(nodeId) / totalWeight);
            }
        }
        
        return weights;
    }
    
    /**
     * Calculates node capability based on resources
     */
    private double calculateNodeCapability(EdgeNode node) {
        if (node == null) return 0.5; // Default for unknown nodes
        
        ResourceCapacity capacity = node.getCapacity();
        
        // Simple capability score based on resource capacities
        return 0.5 * (capacity.getTotalCpu() / 10.0) + 
               0.3 * (capacity.getTotalMemory() / 8000.0) + 
               0.2 * (capacity.getTotalBandwidth() / 1000.0);
    }
    
    /**
     * Distributes global model to all nodes
     */
    private void distributeGlobalModel() {
        for (NodeModel model : nodeModels.values()) {
            model.updateFromGlobal(globalModel);
        }
    }
    
    /**
     * Gets the current global model parameters
     */
    public Map<String, Double> getGlobalParameters() {
        return globalModel.getParameters();
    }
    
    /**
     * Helper method to get node by ID
     */
    private EdgeNode getNodeById(String nodeId) {
        // This would typically come from a node registry or similar service
        // For simplicity, we return null and handle it in the calling method
        return null;
    }
    
    /**
     * Updates resource allocation based on global model
     * Implementation of Equation 35-36 from the paper
     */
    public Map<String, Double> calculateResourceAllocation(String serviceId, 
                                                          Map<String, Double> baseRequirements,
                                                          double pathCriticality) {
        Map<String, Double> allocation = new HashMap<>(baseRequirements);
        
        // Scaling factor γ from the paper
        double scalingFactor = globalModel.getParameter("resourceScalingFactor");
        
        // Apply scaling based on path criticality
        for (String resource : allocation.keySet()) {
            double baseValue = allocation.get(resource);
            double scaledValue = baseValue * (1 + scalingFactor * pathCriticality);
            allocation.put(resource, scaledValue);
        }
        
        return allocation;
    }
    
    /**
     * Inner class representing the global model in federated learning
     */
    private static class GlobalModel {
        private Map<String, Double> parameters;
        
        public GlobalModel() {
            parameters = new HashMap<>();
            initializeDefaultParameters();
        }
        
        private void initializeDefaultParameters() {
            // Critical path threshold (τcrit in paper)
            parameters.put("criticalPathThreshold", 0.7);
            
            // Path weights for different criticality types
            parameters.put("temporalWeight", 0.4);     // α
            parameters.put("resourceWeight", 0.35);    // β
            parameters.put("dependencyWeight", 0.25);  // γ
            
            // Resource allocation parameters
            parameters.put("resourceScalingFactor", 0.3);  // γ in Equation 35
            
            // Utilization threshold
            parameters.put("utilizationThreshold", 0.8);   // Uth
            
            // Chain stability parameters
            parameters.put("adaptationFactor", 0.5);
        }
        
        public Map<String, Double> getParameters() {
            return new HashMap<>(parameters);
        }
        
        public double getParameter(String name) {
            return parameters.getOrDefault(name, 0.0);
        }
        
        public void setParameter(String name, double value) {
            parameters.put(name, value);
        }
    }
    
    /**
     * Inner class representing a node's local model in federated learning
     */
    private static class NodeModel {
        private String nodeId;
        private Map<String, Double> parameters;
        private int sampleCount;
        private double qualityScore;
        
        public NodeModel(String nodeId) {
            this.nodeId = nodeId;
            this.parameters = new HashMap<>();
            this.sampleCount = 0;
            this.qualityScore = 0.8; // Initial quality score
        }
        
        /**
         * Updates model parameters based on node state
         * Implementation of Equation 31 from the paper
         */
        public void updateParameters(NodeState state, double learningRate) {
            // Simple gradient descent update
            // θ(t+1) = θ(t) - η∇L(θ(t))
            Map<String, Double> gradients = calculateGradients(state);
            
            for (String paramName : gradients.keySet()) {
                double currentValue = parameters.getOrDefault(paramName, 0.0);
                double gradient = gradients.get(paramName);
                double newValue = currentValue - learningRate * gradient;
                
                parameters.put(paramName, newValue);
            }
            
            // Increment sample count
            sampleCount++;
            
            // Update quality score based on chain completion rate
            if (state.getLatencyStats().containsKey("chainCompletionRate")) {
                qualityScore = 0.7 * qualityScore + 
                               0.3 * state.getLatencyStats().get("chainCompletionRate");
            }
        }
        
        /**
         * Calculates gradients for parameter updates
         */
        private Map<String, Double> calculateGradients(NodeState state) {
            Map<String, Double> gradients = new HashMap<>();
            
            // Example gradients based on chain latency
            double chainLatency = state.getLatencyStats().getOrDefault("avgChainLatency", 0.0);
            double resourceUtilization = calculateAverageUtilization(state.getResourceState());
            
            // Gradients for criticality weights
            gradients.put("temporalWeight", chainLatency / 100.0);
            gradients.put("resourceWeight", (resourceUtilization - 0.7) * 0.5);
            gradients.put("dependencyWeight", 0.1);
            
            // Critical path threshold gradient
            double completionRate = state.getLatencyStats().getOrDefault("chainCompletionRate", 0.9);
            gradients.put("criticalPathThreshold", (0.95 - completionRate) * 0.2);
            
            // Resource scaling factor gradient
            gradients.put("resourceScalingFactor", (0.7 - resourceUtilization) * 0.3);
            
            return gradients;
        }
        
        /**
         * Updates local model from global model
         */
        public void updateFromGlobal(GlobalModel globalModel) {
            Map<String, Double> globalParams = globalModel.getParameters();
            
            // Adapt global parameters to local model with some local preservation
            for (String paramName : globalParams.keySet()) {
                double globalValue = globalParams.get(paramName);
                double localValue = parameters.getOrDefault(paramName, globalValue);
                
                // 80% global, 20% local preservation
                double newValue = 0.8 * globalValue + 0.2 * localValue;
                parameters.put(paramName, newValue);
            }
        }
        
        /**
         * Calculates average resource utilization
         */
        private double calculateAverageUtilization(Map<String, Double> resourceState) {
            if (resourceState.isEmpty()) return 0.0;
            
            double sum = 0.0;
            for (double value : resourceState.values()) {
                sum += value;
            }
            
            return sum / resourceState.size();
        }
        
        public Map<String, Double> getParameters() {
            return new HashMap<>(parameters);
        }
        
        public int getSampleCount() {
            return sampleCount;
        }
        
        public double getQualityScore() {
            return qualityScore;
        }
    }
    
    /**
     * Inner class representing a node's state in federated learning
     * Implementation of Equation 23-27 from the paper
     */
    private static class NodeState {
        private String nodeId;
        private Map<String, Double> resourceState;              // Ri(t)
        private Map<String, List<Double>> pathCriticality;      // Pi(t)
        private Map<String, Double> latencyStats;               // Li(t)
        private double failRate;
        
        public NodeState(String nodeId) {
            this.nodeId = nodeId;
            this.resourceState = new HashMap<>();
            this.pathCriticality = new HashMap<>();
            this.latencyStats = new HashMap<>();
            this.failRate = 0.05; // Initial failure rate
        }
        
        // Getters and setters
        public String getNodeId() { return nodeId; }
        
        public Map<String, Double> getResourceState() { return resourceState; }
        
        public void setResourceState(Map<String, Double> resourceState) {
            this.resourceState = resourceState;
        }
        
        public Map<String, List<Double>> getPathCriticality() { return pathCriticality; }
        
        public void setPathCriticality(Map<String, List<Double>> pathCriticality) {
            this.pathCriticality = pathCriticality;
        }
        
        public Map<String, Double> getLatencyStats() { return latencyStats; }
        
        public void setLatencyStats(Map<String, Double> latencyStats) {
            this.latencyStats = latencyStats;
            
            // Update fail rate based on service chain completion
            if (latencyStats.containsKey("failedRequests") && 
                latencyStats.containsKey("totalRequests")) {
                double failed = latencyStats.get("failedRequests");
                double total = latencyStats.get("totalRequests");
                
                if (total > 0) {
                    // Smooth update of fail rate
                    this.failRate = 0.9 * this.failRate + 0.1 * (failed / total);
                }
            }
        }
        
        public double getFailRate() { return failRate; }
    }
}