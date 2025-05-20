package main


type PathStep struct {
    Key  int64  `json:"key"`  
    Repr string `json:"repr"` 
}

// Path is the entire sequence of steps of ONE execution.
type Path = []PathStep

// Paths is the entire sequence of steps of ALL executions
type Paths = []Path

// ------------------------------------------------
