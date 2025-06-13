package main

import (
	"errors"
	"fmt"
)

type EventMsg = map[string]interface{}

// type Event struct {
// 	Name   string
// 	Node   string `json:"-"`
// 	Params map[string]interface{}
// 	Reset  bool
// }

func MapEvent(msg EventMsg) (Event, error) {
	eventType, err := GetEventType(msg)
	if err != nil {
		return Event{}, err
	}

	switch eventType {
	case "BecomeLeader":
		return BecomeLeaderEvent(msg)
	case "ClientRequest":
		return ClientRequestEvent(msg)
	case "LogUpdate":
		return LogUpdateEvent(msg)
	case "UpdateSnapshot":
		return UpdateSnapshotEvent(msg)
	case "AdvanceCommitIndex":
		return UpdateSnapshotEvent(msg)
	case "DeliverMessage":
		return DeliverMessageEvent(msg)
	case "Timeout":
		return TimeoutEvent(msg)
	default:
		return EmptyEventWithReason("unknown event type: " + eventType)
	}
}

func BecomeLeaderEvent(msg EventMsg) (Event, error) {
	node, err := GetNode(msg)
	if err != nil {
		return EmptyEventWithError(err)
	}
	return Event{
		Name: "BecomeLeader",
		Node: node,
	}, nil
}

func ClientRequestEvent(msg EventMsg) (Event, error) {
	return EmptyEventWithReason("unimplemented")
}

func LogUpdateEvent(msg EventMsg) (Event, error) {
	return EmptyEventWithReason("unimplemented")
}

func UpdateSnapshotEvent(msg EventMsg) (Event, error) {
	return EmptyEventWithReason("unimplemented")
}

func AdvanceCommitIndexEvent(msg EventMsg) (Event, error) {
	node, err := GetNode(msg)
	if err != nil {
		return EmptyEventWithError(err)
	}
	return Event{
		Name:   "AdvanceCommitIndex",
		Node:   node,
		Params: map[string]interface{}{"i": node},
	}, nil
}

func DeliverMessageEvent(msg EventMsg) (Event, error) {
	return EmptyEventWithReason("unimplemented")
}

func TimeoutEvent(msg EventMsg) (Event, error) {
	node, err := GetNode(msg)
	if err != nil {
		return EmptyEventWithError(err)
	}
	return Event{
		Name: "Timeout",
		Node: node,
	}, nil
}

func EmptyEventWithReason(reason string) (Event, error) {
	return EmptyEventWithError(errors.New(reason))
}

func EmptyEventWithError(err error) (Event, error) {
	return Event{}, err
}

func GetEventType(msg EventMsg) (string, error) {
	eventTypeI, ok := msg["type"]
	if !ok {
		return "", fmt.Errorf("message does not contain type field")
	}
	eventType, ok := eventTypeI.(string)
	if !ok {
		return "", fmt.Errorf("could not cast event type to string")
	}
	return eventType, nil
}

func GetNode(msg EventMsg) (string, error) {
	nodeI, ok := msg["server_id"]
	if !ok {
		return "", fmt.Errorf("message does not contain type field")
	}
	node, ok := nodeI.(string)
	if !ok {
		return "", fmt.Errorf("could not cast event type to string")
	}
	return node, nil
}
