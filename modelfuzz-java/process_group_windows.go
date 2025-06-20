//go:build windows
// +build windows

package main

import (
	"fmt"
	"os/exec"
	"strconv"
	"strings"
	"syscall"
)

// SetupProcessGroup does nothing on Windows
func SetupProcessGroup(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		CreationFlags: syscall.CREATE_NEW_PROCESS_GROUP,
	}
}

// KillProcessGroup kills the main process on Windows
// func KillProcessGroup(pid int) error {
//     // Verifica se il processo esiste prima di provare a terminarlo
//     checkCmd := exec.Command("tasklist", "/FI", fmt.Sprintf("PID eq %d", pid), "/NH")
//     checkOutput, _ := checkCmd.CombinedOutput()
    
//     if !strings.Contains(string(checkOutput), strconv.Itoa(pid)) {
//         return fmt.Errorf("process %d does not exist", pid)
//     }
    
//     cmd := exec.Command("taskkill", "/T", "/F", "/PID", strconv.Itoa(pid))
//     output, err := cmd.CombinedOutput()
//     if err != nil {
//         return fmt.Errorf("kill failed: %v, output: %s", err, output)
//     }
//     return nil
// }

// func KillProcessGroup(pid int) error {
//     // FindProcess on Windows never fails for a valid PID.
//     proc, err := os.FindProcess(pid)
//     if err != nil {
//         return fmt.Errorf("could not find process %d: %w", pid, err)
//     }
//     // Kill on Windows issues a TerminateProcess syscall.
//     if err := proc.Kill(); err != nil {
//         return fmt.Errorf("failed to kill process %d: %w", pid, err)
//     }
//     return nil
// }

func KillProcessGroup(pid int) error {
    cmd := exec.Command("taskkill", "/T", "/F", "/PID", strconv.Itoa(pid))
    output, err := cmd.CombinedOutput()
    if err != nil && !strings.Contains(string(output), "no process was found") {
        return fmt.Errorf("kill failed: %v, output: %s", err, output)
    }
    return nil
}
