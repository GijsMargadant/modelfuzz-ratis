package main

import (
	"fmt"
	"os"
	"path"
	"strconv"
	"strings"
)

type NodeType string

const (
	Xraft NodeType = "xraft"
	Ratis NodeType = "ratis"
)

type Node interface {
	Create()
	Start() error
	Cleanup()
	Stop() error
	GetLogs() (string, string)
}

type Client interface {
	SendRequest()
}

type NodeConfig struct {
	ClusterID         int
	GroupPort         int
	BaseGroupPort     int
	ServicePort       int
	InterceptorPort   int
	SchedulerPort     int
	NodeId            string
	WorkDir           string
	ServerPath        string
	NumNodes          int
	LogConfig         string
	PeerAddresses     string
	trackCodeCoverage bool
	jacocoAgentPath   string
}

type ClusterConfig struct {
	FuzzerType          FuzzerType
	ClusterID           int
	NumNodes            int
	ServerType          NodeType
	XraftServerPath     string
	XraftClientPath     string
	RatisServerPath     string
	RatisClientPath     string
	RatisLog4jConfig    string
	BaseGroupPort       int
	BaseServicePort     int
	BaseInterceptorPort int
	SchedulerPort       int
	RatisDataDir        string
	WorkDir             string
	LogLevel            string
	jacocoLib           string
}

func (c *ClusterConfig) Copy() *ClusterConfig {
	return &ClusterConfig{
		NumNodes:            c.NumNodes,
		ServerType:          c.ServerType,
		XraftServerPath:     c.XraftServerPath,
		XraftClientPath:     c.XraftClientPath,
		RatisServerPath:     c.RatisClientPath,
		RatisClientPath:     c.RatisClientPath,
		RatisLog4jConfig:    c.RatisLog4jConfig,
		BaseGroupPort:       c.BaseGroupPort,
		BaseServicePort:     c.BaseServicePort,
		BaseInterceptorPort: c.BaseInterceptorPort,
		SchedulerPort:       c.SchedulerPort,
		RatisDataDir:        c.RatisDataDir,
		WorkDir:             c.WorkDir,
		LogLevel:            c.LogLevel,
		jacocoLib:           c.jacocoLib,
	}
}

func (c *ClusterConfig) SetDefaults() {
	if c.XraftServerPath == "" {
		c.XraftServerPath = "../xraft-controlled/xraft-kvstore/target/xraft-kvstore-0.1.0-SNAPSHOT-bin/xraft-kvstore-0.1.0-SNAPSHOT/bin/xraft-kvstore"
		// TODO
	}
	if c.XraftClientPath == "" {
		c.XraftClientPath = "../xraft-controlled/xraft-kvstore/target/xraft-kvstore-0.1.0-SNAPSHOT-bin/xraft-kvstore-0.1.0-SNAPSHOT/bin/xraft-kvstore-cli"
		// TODO
	}
	if c.WorkDir == "" {
		c.WorkDir = fmt.Sprintf("output/%s/tmp/%d", c.FuzzerType.String(), c.ClusterID)
	}
	os.MkdirAll(c.WorkDir, 0777)

	if c.LogLevel == "" {
		c.LogLevel = "INFO"
	}
}

func (c *ClusterConfig) GetNodeConfig(id string, nodeType NodeType) *NodeConfig {
	nodeWorkDir := path.Join(c.WorkDir, id)
	if _, err := os.Stat(nodeWorkDir); err == nil {
		os.RemoveAll(nodeWorkDir)
	}
	os.MkdirAll(nodeWorkDir, 0777)
	idInt, err := strconv.Atoi(id)
	if err != nil {
		return nil
	}
	var serverPath string
	var logConfig string
	var peerAddresses string
	if nodeType == Xraft {
		serverPath = c.XraftServerPath
		logConfig = ""
		peerAddresses = ""
	} else {
		serverPath = c.RatisServerPath
		logConfig = c.RatisLog4jConfig
		peerAddresses = ""
		for i := 0; i < c.NumNodes; i++ {
			peerAddresses += "127.0.0.1:" + strconv.Itoa(c.BaseGroupPort+i) + ","
		}
		peerAddresses = peerAddresses[:len(peerAddresses)-1]
	}

	return &NodeConfig{
		ClusterID:         c.ClusterID,
		GroupPort:         c.BaseGroupPort + idInt,
		BaseGroupPort:     c.BaseGroupPort,
		ServicePort:       c.BaseServicePort + idInt,
		InterceptorPort:   c.BaseInterceptorPort + idInt,
		SchedulerPort:     c.SchedulerPort,
		NodeId:            id,
		WorkDir:           nodeWorkDir,
		ServerPath:        serverPath,
		NumNodes:          c.NumNodes,
		LogConfig:         logConfig,
		PeerAddresses:     peerAddresses,
		trackCodeCoverage: strings.Contains(strings.ToLower((c.FuzzerType.String())), "code"),
		jacocoAgentPath:   path.Join(c.jacocoLib, "jacocoagent.jar"),
	}
}

type Cluster struct {
	Nodes  map[string]Node
	Config *ClusterConfig
	logger *Logger
	Client Client // TODO
}

func NewCluster(config *ClusterConfig, logger *Logger) *Cluster {
	config.SetDefaults()
	var client Client
	if config.ServerType == Xraft {
		client = NewXraftClient(config.NumNodes, config.BaseServicePort, config.XraftClientPath, logger)
	} else {
		peerAddresses := ""
		for i := 0; i < config.NumNodes; i++ {
			peerAddresses += "127.0.0.1:" + strconv.Itoa(config.BaseGroupPort+i) + ","
		}
		peerAddresses = peerAddresses[:len(peerAddresses)-1]
		client = NewRatisClient(config.RatisClientPath, peerAddresses, config.RatisLog4jConfig, logger)
	}

	c := &Cluster{
		Config: config,
		Nodes:  make(map[string]Node),
		logger: logger,
		Client: client,
	}

	for i := 1; i <= config.NumNodes; i++ {
		nConfig := config.GetNodeConfig(strconv.Itoa(i), config.ServerType)
		if config.ServerType == Xraft {
			c.Nodes[strconv.Itoa(i)] = NewXraftNode(nConfig, c.logger.With(LogParams{"node": i}))
		} else {
			c.Nodes[strconv.Itoa(i)] = NewRatisNode(nConfig, c.logger.With(LogParams{"node": i}))
		}
	}
	c.logger.Debug("Initialized cluster.")
	return c
}

func (c *Cluster) Start() error {
	c.logger.Debug("Starting cluster...")
	for i := 1; i <= c.Config.NumNodes; i++ {
		node := c.Nodes[strconv.Itoa(i)]
		node.Start()
		// if err := node.Start(); err != nil {
		// 	return fmt.Errorf("Error starting node %d: %s", i, err)
		// }
	}
	return nil
}

func (c *Cluster) Destroy() error {
	c.logger.Debug("Destroying cluster...")
	var err error = nil
	for _, node := range c.Nodes {
		err = node.Stop()
		node.Cleanup()
	}

	os.RemoveAll(c.Config.RatisDataDir)
	os.RemoveAll(c.Config.WorkDir)
	return err
}

func (c *Cluster) GetNode(nodeId string) (Node, bool) {
	node, ok := c.Nodes[nodeId]
	return node, ok

}

func (c *Cluster) GetLogs() string {
	logLines := []string{}
	for nodeID, node := range c.Nodes {
		logLines = append(logLines, fmt.Sprintf("Logs for node: %s\n", nodeID))
		stdout, stderr := node.GetLogs()
		logLines = append(logLines, "----- Stdout -----", stdout, "----- Stderr -----", stderr)
	}
	return strings.Join(logLines, "\n")
}

func (c *Cluster) SendRequest() {
	c.Client.SendRequest()
}
