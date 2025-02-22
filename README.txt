# SDT-MCS: 拓扑感知微服务编排与自适应学习系统

## 项目概述

SDT-MCS（面向服务依赖拓扑的微服务协同调度）是一个结合拓扑导向联邦学习与轻量级Actor-Critic强化机制的框架，用于云边环境中高效的微服务编排。该系统实现了研究论文中描述的算法和技术，旨在解决工业物联网和智慧城市场景中复杂服务链的编排挑战。

## 核心特性

- **拓扑感知预部署算法**：同时考虑资源约束和服务依赖关系，有效防止关键路径服务的资源争用，降低服务链的平均延迟。
- **基于链式的调度机制**：采用Actor-Critic强化学习进行本地动态调整，实现对工作负载变化的快速响应，同时保持服务链稳定性。
- **自适应学习系统**：平衡全局优化与本地响应，适应复杂的云边环境。
- **支持复杂服务链**：专为工业物联网和智慧城市场景中常见的复杂服务链设计。

## 系统架构

系统由以下主要模块组成：

1. **拓扑管理器(Topology Manager)**：
   - 维护服务依赖关系的全面视图
   - 监控链级性能指标
   - 识别关键服务路径

2. **资源编排器(Resource Orchestrator)**：
   - 处理资源分配和服务放置决策
   - 利用拓扑信息优化服务部署
   - 防止资源争用，同时最小化服务间通信开销

3. **链式调度器(Chain Scheduler)**：
   - 管理服务链的运行时执行
   - 实现负载均衡和请求路由
   - 监控系统性能，包括端到端处理延迟和资源利用率

4. **学习引擎(Learning Engine)**：
   - 实现两层学习机制（联邦学习和强化学习）
   - 联邦模型聚合，同时保护隐私
   - 动态优化系统行为


### 运行示例

```java
// 初始化模型
ServiceDependencyGraph graph = new ServiceDependencyGraph();
Map<String, EdgeNode> nodes = new HashMap<>();

// 添加服务和依赖关系
Microservice service1 = new Microservice("s1", ResourceRequirements.forServiceType("computation-intensive"), 10.0);
Microservice service2 = new Microservice("s2", ResourceRequirements.forServiceType("io-intensive"), 15.0);
graph.addService(service1);
graph.addService(service2);
graph.addDependency("s1", "s2", 50.0, 0.8);

// 创建边缘和云节点
nodes.put("edge1", EdgeNode.createEdgeNode("edge1"));
nodes.put("cloud1", EdgeNode.createCloudNode("cloud1"));

// 初始化关键路径分析器
CriticalPathAnalyzer pathAnalyzer = new CriticalPathAnalyzer(graph, nodes);

// 部署服务
TopologyAwareDeployment deployment = new TopologyAwareDeployment(graph, nodes);
Map<String, String> placement = deployment.executeDeployment();

// 启动链式调度器
ChainScheduler scheduler = new ChainScheduler(graph, nodes, placement);
scheduler.start();

// 提交请求
Map<String, Object> requestData = new HashMap<>();
requestData.put("input_data", "sample-data");
CompletableFuture<ChainScheduler.ChainResponse> responseFuture = 
    scheduler.submitRequest("s1-s2", requestData);

// 获取结果
ChainScheduler.ChainResponse response = responseFuture.get();
System.out.println("执行结果: " + response.getResults());
System.out.println("执行延迟: " + response.getLatency() + "ms");
```

## 主要文件说明

### 模型层 (model)

- **ServiceDependencyGraph.java**: 实现服务依赖拓扑图，以有向无环图(DAG)表示服务间关系。
- **Microservice.java**: 定义微服务属性和行为，包括动态资源需求计算算法。
- **ResourceRequirements.java**: 表示微服务资源需求，提供资源验证和扩展功能。
- **ResourceCapacity.java**: 管理节点资源，处理分配和释放。
- **EdgeNode.java**: 表示云边环境中的计算节点，管理服务部署和网络延迟。

### 分析层 (analysis)

- **CriticalPathAnalyzer.java**: 实现多维关键路径分析，考虑时间、资源和依赖临界性。

### 部署层 (deployment)

- **TopologyAwareDeployment.java**: 基于拓扑感知实现服务预部署，优化服务放置决策。

### 调度层 (scheduling)

- **ActorCriticScheduler.java**: 使用Actor-Critic强化学习进行本地优化，实现快速响应。
- **ChainScheduler.java**: 实现链式服务调度，处理请求路由和负载均衡。

### 监控层 (monitoring)

- **MonitoringFramework.java**: 收集性能指标，提供自适应采样机制。

### 学习层 (learning)

- **FederatedLearningEngine.java**: 实现联邦学习机制，优化全局拓扑。

