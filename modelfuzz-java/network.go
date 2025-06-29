package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
)

type Network struct {
	port   int
	ctx    context.Context
	server *http.Server
	logger *Logger

	clientRequestQueue []int
	leader             string
	lock               *sync.Mutex
	nodes              map[string]string
	mailboxes          map[string][]Message
	Events             *EventTrace
	requestMap         map[string]int
	requestCounter     int
}

func (n *Network) AddClientRequestEvent(requestCount int) {
	if n.leader == "" {
		n.clientRequestQueue = append(n.clientRequestQueue, requestCount)
	} else {
		leader, _ := strconv.Atoi(n.leader)
		n.AddEvent(Event{
			Name: "ClientRequest",
			Params: map[string]interface{}{
				"leader":  leader,
				"request": requestCount,
			},
		})
	}
}

type Message struct {
	From string `json:"from"`
	To   string `json:"to"`
	Data string `json:"data"`
	Type string `json:"type"`
	// ID            string                 `json:"id"`
	ParsedMessage map[string]interface{} `json:"-"`
}

type entry struct {
	Term int    `json:"Term"`
	Data string `json:"Data"`
}

func (m Message) to() string {
	return m.To
}

func (m Message) from() string {
	return m.From
}

func (m Message) Copy() Message {
	n := Message{
		From: m.From,
		To:   m.To,
		Data: m.Data,
		Type: m.Type,
		// ID:            m.ID,
		ParsedMessage: make(map[string]interface{}),
	}
	if m.ParsedMessage != nil {
		for k, v := range m.ParsedMessage {
			n.ParsedMessage[k] = v
		}
	}
	return n
}

func NewNetwork(ctx context.Context, port int, logger *Logger) *Network {
	n := &Network{
		port:               port,
		ctx:                ctx,
		lock:               new(sync.Mutex),
		nodes:              make(map[string]string),
		mailboxes:          make(map[string][]Message),
		Events:             NewEventTrace(),
		logger:             logger,
		clientRequestQueue: make([]int, 0),
		leader:             "",
		requestMap:         make(map[string]int),
		requestCounter:     0,
	}

	// gin.SetMode(gin.DebugMode)
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	// r.Use(gin.Logger())   // Logs every request to stdout
	// r.Use(gin.Recovery()) // Recovers from panics
	r.POST("/replica", n.handleReplica)
	r.POST("/event", n.handleEvent)
	r.POST("/message", n.handleMessage)
	n.server = &http.Server{
		Addr:         fmt.Sprintf("localhost:%d", port),
		Handler:      r,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 5 * time.Second,
	}
	n.logger.Debug("Created network.")

	return n
}

func (n *Network) handleMessage(c *gin.Context) {
	var raw map[string]interface{}
	if err := c.ShouldBindJSON(&raw); err != nil {
		n.logger.With(LogParams{"error": err.Error()}).Debug("Failed to unmarshal request")
		c.JSON(http.StatusBadRequest, gin.H{"error": "failed to unmarshal request"})
		return
	}

	from, ok1 := raw["from"].(string)
	to, ok2 := raw["to"].(string)
	msgType, ok3 := raw["type"].(string)
	data, ok4 := raw["data"].(string)
	paramsRaw, ok5 := raw["params"]

	if !ok1 || !ok2 || !ok3 || !ok4 || !ok5 {
		n.logger.With(LogParams{"error": "missing required fields"}).Debug("Invalid message format")
		c.JSON(http.StatusBadRequest, gin.H{"error": "missing required fields"})
		return
	}

	params, ok := paramsRaw.(map[string]interface{})
	if !ok {
		n.logger.With(LogParams{"error": "params field is not a valid object"}).Debug("Invalid params format")
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid params format"})
		return
	}

	m := Message{
		From:          from,
		To:            to,
		Type:          msgType,
		Data:          data,
		ParsedMessage: params,
	}

	// n.logger.With(LogParams{"message": params}).Debug("received message")

	mKey := fmt.Sprintf("%s_%s", from, to)

	n.lock.Lock()
	if _, ok := n.mailboxes[mKey]; !ok {
		n.mailboxes[mKey] = make([]Message, 0)
	}
	n.mailboxes[mKey] = append(n.mailboxes[mKey], m.Copy())
	n.lock.Unlock()

	c.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func (n *Network) handleReplica(c *gin.Context) {
	replica := make(map[string]interface{})
	if err := c.ShouldBindJSON(&replica); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "failed to unmarshal request"})
		return
	}
	n.logger.With(LogParams{"replica": replica}).Debug("recieved replica info")
	nodeID := "1"
	nodeIDI, ok := replica["id"]
	if !ok {
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}
	nodeIDS, ok := nodeIDI.(string)
	if !ok {
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}
	nodeID = nodeIDS // nodeID, err := strconv.Atoi(nodeIDS)
	// if err != nil {
	// 	c.JSON(http.StatusOK, gin.H{"message": "ok"})
	// 	return
	// }

	nodeAddrI, ok := replica["addr"]
	if !ok {
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}
	nodeAddr, ok := nodeAddrI.(string)
	if !ok {
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}

	n.lock.Lock()
	n.nodes[nodeID] = nodeAddr
	n.lock.Unlock()

	c.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func (n *Network) handleEvent(c *gin.Context) {
	// event := make(map[string]interface{})
	// if err := c.ShouldBindJSON(&event); err != nil {
	// 	c.JSON(http.StatusBadRequest, gin.H{"error": "failed to unmarshal request"})
	// 	return
	// }
	body, err := io.ReadAll(c.Request.Body)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "failed to read body"})
		return
	}

	event, err := decodePossiblyDoubleEncodedJSON(body)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "failed to decode body to JSON"})
		return
	}

	n.logger.With(LogParams{"event": event}).Debug("received event")
	nodeID := "1"
	nodeIDI, ok := event["server_id"]
	if !ok {
		n.logger.Debug("Could not find server_id")
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}
	nodeIDS, ok := nodeIDI.(string)
	if !ok {
		n.logger.Debug("Could not cast server_id")
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}
	nodeID = nodeIDS

	eventTypeI, ok := event["type"]
	if !ok {
		n.logger.Debug("Could not find type")
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}
	eventType, ok := eventTypeI.(string)
	if !ok {
		n.logger.Debug("Could not cast type")
		c.JSON(http.StatusOK, gin.H{"message": "ok"})
		return
	}

	e := Event{
		Name:   eventType,
		Node:   nodeID,
		Params: n.mapEventToParams(eventType, event),
	}

	n.logger.With(LogParams{"e": e}).Debug("Appending event")
	n.lock.Lock()
	n.Events.Add(e)
	n.lock.Unlock()
	c.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func decodePossiblyDoubleEncodedJSON(data []byte) (map[string]interface{}, error) {
	var outer interface{}
	if err := json.Unmarshal(data, &outer); err != nil {
		return nil, err
	}
	switch v := outer.(type) {
	case string:
		var inner map[string]interface{}
		err := json.Unmarshal([]byte(v), &inner)
		return inner, err
	case map[string]interface{}:
		return v, nil
	default:
		return nil, fmt.Errorf("unexpected JSON type")
	}
}

func (n *Network) mapEventToParams(t string, e map[string]interface{}) map[string]interface{} {
	params := make(map[string]interface{})
	eParams := e // e["params"].(map[string]interface{})
	switch t {
	case "BecomeLeader":
		node, _ := strconv.Atoi(eParams["node"].(string))
		term := int(eParams["term"].(float64))
		params["node"] = node
		params["term"] = term

		n.leader = eParams["node"].(string)
		for _, req := range n.clientRequestQueue {
			n.AddEvent(Event{
				Name: "ClientRequest",
				Params: map[string]interface{}{
					"leader":  n.leader,
					"request": req,
				},
			})
		}
		n.clientRequestQueue = make([]int, 0)
	case "Timeout":
		node, _ := strconv.Atoi(eParams["node"].(string))
		params["node"] = node
	case "UpdateSnapshot":
		node, _ := strconv.Atoi(eParams["node"].(string))
		params["node"] = node
		params["snapshot_index"] = int(eParams["snapshot_index"].(float64))
	case "AdvanceCommitIndex":
		node, _ := strconv.Atoi(eParams["server_id"].(string))
		params["node"] = node
		params["i"] = node
	default:
		params = eParams
	}

	return params
}

func (n *Network) getRequestNumber(str string) int {
	_, ok := n.requestMap[str]
	if !ok {
		n.requestMap[str] = n.requestCounter
		n.requestCounter++
	}
	return n.requestMap[str]
}

func (n *Network) getMessageEventParams(m Message) map[string]interface{} {
	params := make(map[string]interface{})

	params["term"] = int(m.ParsedMessage["term"].(float64))
	from, _ := strconv.Atoi(m.From)
	to, _ := strconv.Atoi(m.To)
	params["from"] = from
	params["to"] = to

	switch m.Type {
	case "append_entries_request":
		params["type"] = "MsgApp"
		params["log_term"] = m.ParsedMessage["prev_log_term"]
		entries := make([]entry, 0)
		for _, eI := range m.ParsedMessage["entries"].(map[string]interface{}) {
			e := eI.(map[string]interface{})
			data := e["data"].(string)
			if data == "" {
				continue
			}
			eTermI, ok := e["term"]
			if !ok {
				continue
			}

			entries = append(entries, entry{
				Term: int(eTermI.(float64)),
				Data: strconv.Itoa(n.getRequestNumber(data)),
			})
		}
		params["entries"] = entries
		params["index"] = m.ParsedMessage["prev_log_idx"]
		if m.ParsedMessage["prev_log_idx"] == nil {
			params["index"] = 0
		}
		params["commit"] = m.ParsedMessage["leader_commit"]
		params["reject"] = false
	case "append_entries_reply":
		params["type"] = "MsgAppResp"
		params["log_term"] = 0
		params["entries"] = []entry{}
		params["index"] = m.ParsedMessage["current_idx"]
		params["commit"] = 0
		params["reject"] = int(m.ParsedMessage["success"].(float64)) == 0
	case "request_vote_request":
		params["type"] = "MsgVote"
		params["log_term"] = m.ParsedMessage["last_log_term"]
		params["entries"] = []entry{}
		params["index"] = m.ParsedMessage["last_log_idx"]
		params["commit"] = 0
		params["reject"] = false
	case "request_vote_reply":
		params["type"] = "MsgVoteResp"
		params["log_term"] = 0
		params["entries"] = []entry{}
		params["index"] = 0
		params["commit"] = 0
		params["reject"] = int(m.ParsedMessage["reject"].(float64)) == 0
	case "AdvanceCommitIndex":
		params["type"] = "AdvanceCommitIndex"
		params["i"] = m.ParsedMessage["server_id"]
	}
	return params
}

func (n *Network) Start() {
	n.logger.Debug("Starting network...")
	go func() {
		n.server.ListenAndServe()
	}()

	go func() {
		<-n.ctx.Done()
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		n.server.Shutdown(ctx)
	}()
}

// Shutdown stops the server
func (n *Network) Shutdown() {
	select {
	case <-n.ctx.Done():
		return
	default:
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	n.server.Shutdown(ctx)
}

func (n *Network) Reset() {
	n.logger.Debug("Resetting network...")
	n.lock.Lock()
	defer n.lock.Unlock()

	n.Events = NewEventTrace()
	n.mailboxes = make(map[string][]Message)
	n.nodes = make(map[string]string)
	n.clientRequestQueue = make([]int, 0)
	n.leader = ""
	n.requestMap = make(map[string]int)
	n.requestCounter = 0
}

func (n *Network) GetEventTrace() *EventTrace {
	n.lock.Lock()
	defer n.lock.Unlock()

	return n.Events.Copy()
}

func (n *Network) AddEvent(e Event) {
	n.lock.Lock()
	defer n.lock.Unlock()
	n.Events.Add(e)
}

func (n *Network) WaitForNodes(numNodes int) bool {
	timeout := time.After(2 * time.Second)
	numConnectedNodes := 0
	for numConnectedNodes != numNodes {
		select {
		case <-n.ctx.Done():
			return false
		case <-timeout:
			return false
		case <-time.After(1 * time.Millisecond):
		}
		n.lock.Lock()
		numConnectedNodes = len(n.nodes)
		n.lock.Unlock()
	}
	return true
}

func (n *Network) Schedule(from, to string, maxMessages int) {
	messagesToSend := make([]Message, 0)
	nodeAddr := ""
	mKey := fmt.Sprintf("%s_%s", from, to)
	n.lock.Lock()
	mailbox, ok := n.mailboxes[mKey]

	if ok {
		offset := 0
		for i, m := range mailbox {
			if i < maxMessages {
				messagesToSend = append(messagesToSend, m.Copy())
				offset = i
			}
		}
		if offset == len(mailbox)-1 {
			n.mailboxes[mKey] = make([]Message, 0)
		} else {
			n.mailboxes[mKey] = n.mailboxes[mKey][offset:]
		}
	}
	nodeAddr = n.nodes[to]
	n.lock.Unlock()

	client := &http.Client{
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout:   5 * time.Second,
				KeepAlive: 5 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   5 * time.Second,
			ResponseHeaderTimeout: 5 * time.Second,
			ExpectContinueTimeout: 1 * time.Second,
			DisableKeepAlives:     true,
		},
	}

	for _, m := range messagesToSend {
		go func(m Message, addr string, client *http.Client) {
			msg := map[string]interface{}{
				"from":   m.From,
				"to":     m.To,
				"type":   m.Type,
				"data":   m.Data,
				"params": m.ParsedMessage,
			}
			jsonBytes, err := json.Marshal(msg)
			if err != nil {
				return
			}
			escaped, err := json.Marshal(string(jsonBytes))
			if err != nil {
				return
			}
			bs := escaped
			n.logger.With(LogParams{
				"message": string(bs),
			}).Debug("sending message to: " + "http://" + addr + "/schedule_" + from)
			resp, err := client.Post("http://"+addr+"/schedule_"+from, "application/json", bytes.NewBuffer(bs))
			if err == nil {
				io.ReadAll(resp.Body)
				resp.Body.Close()
			}
		}(m.Copy(), nodeAddr, client)

		receiveEvent := Event{
			Name:   "DeliverMessage",
			Node:   m.To,
			Params: n.getMessageEventParams(m),
		}
		n.lock.Lock()
		n.Events.Add(receiveEvent)
		n.lock.Unlock()
	}
}
