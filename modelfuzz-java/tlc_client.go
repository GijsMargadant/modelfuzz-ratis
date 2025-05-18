package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

type TLCResponse struct {
	States []string
	Keys   []int64
}

type TLCClient struct {
	ClientAddr string
}

type TLCState struct {
	Repr string
	Key  int64
}

func NewTLCClient(addr string) *TLCClient {
	return &TLCClient{
		ClientAddr: addr,
	}
}

func (c *TLCClient) SendTrace(trace *EventTrace) ([]TLCState, error) {
	trace.Add(Event{Reset: true})
	data, err := json.Marshal(trace.Events)
	fmt.Println(string(data[:]))
	if err != nil {
		return []TLCState{}, fmt.Errorf("error marshalling json: %s", err)
	}
	res, err := http.Post("http://"+c.ClientAddr+"/execute", "application/json", bytes.NewBuffer(data))
	if err != nil {
		return []TLCState{}, fmt.Errorf("error sending trace to tlc: %s", err)
	}
	defer res.Body.Close()
	resData, err := io.ReadAll(res.Body)
	if err != nil {
		return []TLCState{}, fmt.Errorf("error reading response from tlc: %s", err)
	}
	tlcResponse := &TLCResponse{}
	if err = json.Unmarshal(resData, tlcResponse); err != nil {
		return []TLCState{}, fmt.Errorf("error parsing tlc response: %s", err)
	}
	result := make([]TLCState, len(tlcResponse.States))
	for i, s := range tlcResponse.States {
		result[i] = TLCState{Repr: s, Key: tlcResponse.Keys[i]}
	}
	return result, nil
}
