/**
 * ServiceDependencyGraph.java
 * 
 * This class represents the service dependency topology as a directed acyclic graph (DAG).
 * It is a core component of the SDT-MCS framework, capturing the relationships and 
 * dependencies between microservices in cloud-edge environments.
 */
package com.sdtmcs.model;

import java.util.*;

public class ServiceDependencyGraph {
    private Map<String, Microservice> services;
    private Map<String, List<Edge>> adjacencyList;
    
    public ServiceDependencyGraph() {
        services = new HashMap<>();
        adjacencyList = new HashMap<>();
    }
    
    /**
     * Adds a new microservice to the dependency graph
     * 
     * @param service The microservice to add
     */
    public void addService(Microservice service) {
        services.put(service.getId(), service);
        if (!adjacencyList.containsKey(service.getId())) {
            adjacencyList.put(service.getId(), new ArrayList<>());
        }
    }
    
    /**
     * Adds a dependency edge between source and target microservices
     * 
     * @param sourceId ID of the source microservice
     * @param targetId ID of the target microservice
     * @param dataVolume The data volume transmitted between services
     * @param frequency The invocation frequency between services
     */
    public void addDependency(String sourceId, String targetId, double dataVolume, double frequency) {
        if (!services.containsKey(sourceId) || !services.containsKey(targetId)) {
            throw new IllegalArgumentException("Source or target service not found");
        }
        
        Edge edge = new Edge(sourceId, targetId, dataVolume, frequency);
        adjacencyList.get(sourceId).add(edge);
    }
    
    /**
     * Gets all paths from source to target service in the dependency graph
     * 
     * @param sourceId ID of the source microservice
     * @param targetId ID of the target microservice
     * @return List of all paths between source and target
     */
    public List<List<String>> getAllPaths(String sourceId, String targetId) {
        List<List<String>> paths = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<String> currentPath = new ArrayList<>();
        
        currentPath.add(sourceId);
        getAllPathsDFS(sourceId, targetId, visited, currentPath, paths);
        
        return paths;
    }
    
    /**
     * Depth-first search helper for path finding
     */
    private void getAllPathsDFS(String currentId, String targetId, Set<String> visited, 
                              List<String> currentPath, List<List<String>> paths) {
        if (currentId.equals(targetId)) {
            paths.add(new ArrayList<>(currentPath));
            return;
        }
        
        visited.add(currentId);
        
        for (Edge edge : adjacencyList.get(currentId)) {
            String neighborId = edge.getTargetId();
            if (!visited.contains(neighborId)) {
                currentPath.add(neighborId);
                getAllPathsDFS(neighborId, targetId, visited, currentPath, paths);
                currentPath.remove(currentPath.size() - 1);
            }
        }
        
        visited.remove(currentId);
    }
    
    /**
     * Calculates the cumulative latency for a linear chain of services
     * 
     * @param path The path of service IDs
     * @return The total end-to-end latency for the path
     */
    public double calculateSequentialLatency(List<String> path) {
        double totalLatency = 0.0;
        
        // Add execution times for each service
        for (String serviceId : path) {
            totalLatency += services.get(serviceId).getExecutionTime();
        }
        
        // Add communication times between services
        for (int i = 0; i < path.size() - 1; i++) {
            String source = path.get(i);
            String target = path.get(i + 1);
            
            // Find the edge between source and target
            Edge edge = findEdge(source, target);
            if (edge != null) {
                totalLatency += calculateCommunicationTime(edge);
            }
        }
        
        return totalLatency;
    }
    
    /**
     * Finds the edge between source and target services
     */
    private Edge findEdge(String sourceId, String targetId) {
        List<Edge> edges = adjacencyList.get(sourceId);
        for (Edge edge : edges) {
            if (edge.getTargetId().equals(targetId)) {
                return edge;
            }
        }
        return null;
    }
    
    /**
     * Calculates the communication time between two services based on data volume and network capacity
     */
    private double calculateCommunicationTime(Edge edge) {
        Microservice source = services.get(edge.getSourceId());
        Microservice target = services.get(edge.getTargetId());
        
        // If services are on the same node, use local communication time
        if (source.getNodeId().equals(target.getNodeId())) {
            return edge.getDataVolume() / 1000.0; // Local transfer is faster
        } else {
            // Calculate based on network delay and bandwidth between nodes
            return edge.getDataVolume() / 100.0; // Simplified calculation
        }
    }
    
    /**
     * Gets all services in the dependency graph
     */
    public Collection<Microservice> getAllServices() {
        return services.values();
    }
    
    /**
     * Gets all dependencies from a service
     */
    public List<Edge> getDependencies(String serviceId) {
        return adjacencyList.getOrDefault(serviceId, Collections.emptyList());
    }
    
    /**
     * Edge inner class representing dependencies between services
     */
    public static class Edge {
        private String sourceId;
        private String targetId;
        private double dataVolume;
        private double frequency;
        
        public Edge(String sourceId, String targetId, double dataVolume, double frequency) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.dataVolume = dataVolume;
            this.frequency = frequency;
        }
        
        public String getSourceId() { return sourceId; }
        public String getTargetId() { return targetId; }
        public double getDataVolume() { return dataVolume; }
        public double getFrequency() { return frequency; }
    }
}