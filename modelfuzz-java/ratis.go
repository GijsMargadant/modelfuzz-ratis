package main

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

type RatisNode struct {
	ID      string
	logger  *Logger
	process *exec.Cmd
	config  *NodeConfig

	stdout *bytes.Buffer
	stderr *bytes.Buffer
	cancel context.CancelFunc
}

func NewRatisNode(config *NodeConfig, logger *Logger) *RatisNode {
	return &RatisNode{
		ID:      config.NodeId,
		logger:  logger,
		process: nil,
		config:  config,
		stdout:  nil,
		stderr:  nil,
	}
}

func (x *RatisNode) Create() {
	serverArgs := []string{
		x.config.LogConfig,
		"-cp",
		x.config.ServerPath,
		"org.apache.ratis.examples.counter.server.CounterServer",
		strconv.Itoa(x.config.ClusterID),
		strconv.Itoa(x.config.SchedulerPort),
		strconv.Itoa(x.config.InterceptorPort),
		x.config.NodeId,
		x.config.PeerAddresses,
		"02511d47-d67c-49a3-9011-abb3109a44c1", // TODO - May need to cycle
		"0",
	}
	
	x.logger.With(LogParams{"server-args": strings.Join(serverArgs, " ")}).Debug("Creating server...")
	ctx, cancel := context.WithCancel(context.Background())
	x.process = exec.CommandContext(ctx, "java", serverArgs...)
	x.cancel = cancel
	
	if x.stdout == nil {
		x.stdout = new(bytes.Buffer)
	}
	if x.stderr == nil {
		x.stderr = new(bytes.Buffer)
	}
	x.process.Stdout = x.stdout
	x.process.Stderr = x.stderr
	
	err := x.process.Start()
	if err != nil {
		x.logger.Debug("Error while creating process: " + string(err.Error()))
	}
}

func (x *RatisNode) Start() error {
	x.logger.Debug("Starting node...")
	x.Create()
	if x.process == nil {
		return errors.New("ratis server not started")
	}
	return x.process.Start()
}

func (x *RatisNode) Cleanup() {
	os.RemoveAll(x.config.WorkDir)
}

func (x *RatisNode) Stop() error {
	x.logger.Debug("Stopping node...")
	if x.process == nil {
		return errors.New("ratis server not started")
	}

	x.cancel()
	x.process = nil
	return nil
}

func (x *RatisNode) GetLogs() (string, string) {
	if x.stdout == nil || x.stderr == nil {
		x.logger.Debug("Nil stdout or stderr.")
		return "", ""
	}

	return x.stdout.String(), x.stderr.String()
}

type RatisClient struct {
	ClientBinary     string
	logger           *Logger
	RatisLog4jConfig string
	PeerAddresses    string
}

func NewRatisClient(clientBinary, peerAddresses, log4jConfig string, logger *Logger) *RatisClient {
	return &RatisClient{
		ClientBinary:     clientBinary,
		logger:           logger,
		RatisLog4jConfig: log4jConfig,
		PeerAddresses:    peerAddresses,
	}
}

func (c *RatisClient) SendRequest() {
	c.logger.Debug("Sending client request...")

	clientArgs := []string{
		c.RatisLog4jConfig,
		"-cp",
		c.ClientBinary,
		"org.apache.ratis.examples.counter.client.CounterClient",
		"1",
		c.PeerAddresses,
		"02511d47-d67c-49a3-9011-abb3109a44c1",
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	command := exec.CommandContext(ctx, "java", clientArgs...)
	err := command.Start()
	if err != nil {
		c.logger.Error(fmt.Sprintf("Process exited with error: %v", err))
	} else {
		c.logger.Debug("Process finished successfully")
	}
}
